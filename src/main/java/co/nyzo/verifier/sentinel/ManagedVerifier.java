package co.nyzo.verifier.sentinel;

import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.KeyUtil;
import co.nyzo.verifier.nyzoString.NyzoString;
import co.nyzo.verifier.nyzoString.NyzoStringEncoder;
import co.nyzo.verifier.nyzoString.NyzoStringPrivateSeed;
import co.nyzo.verifier.nyzoString.NyzoStringPublicIdentifier;
import co.nyzo.verifier.util.PrintUtil;

import java.util.Arrays;

public class ManagedVerifier {

    private String host;
    private int port;
    private byte[] seed;
    private byte[] identifier;
    private boolean sentinelTransactionEnabled;
    private byte[] responseIdentifier;

    // These are used to track the health of the verifier.
    private int[] queryResults;
    private int queryIndex;
    private boolean queriedLastInterval;

    public static final int queryResultNotYetQueriedValue = -2;
    public static final int queryResultErrorValue = -1;
    public static final int queryHistoryLength = 10;

    private ManagedVerifier(String host, int port, byte[] seed, byte[] identifier, boolean sentinelTransactionEnabled) {

        // Store the verifier properties. When the public identifier is directly specified, the seed for the private key
        // is empty.
        this.host = host;
        this.port = port;
        this.seed = seed;
        this.identifier = identifier;
        this.sentinelTransactionEnabled = sentinelTransactionEnabled;

        this.queryResults = new int[queryHistoryLength];
        Arrays.fill(this.queryResults, queryResultNotYetQueriedValue);
        this.queryIndex = 0;
        this.queriedLastInterval = false;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public byte[] getSeed() {
        return seed;
    }

    public byte[] getIdentifier() {
        return identifier;
    }

    public boolean isSentinelTransactionEnabled() {
        return sentinelTransactionEnabled;
    }

    public boolean hasPrivateKey() {
        return !ByteUtil.isAllZeros(seed);
    }

    public byte[] getResponseIdentifier() {
        return responseIdentifier;
    }

    public void setResponseIdentifier(byte[] responseIdentifier) {
        this.responseIdentifier = responseIdentifier;
    }

    public static ManagedVerifier fromString(String value) {

        ManagedVerifier result = null;
        if (value != null && !value.isEmpty()) {
            try {
                String[] split = value.split(":");
                if (split.length >= 3) {
                    String host = split[0];

                    int port = -1;
                    try {
                        port = Integer.parseInt(split[1]);
                    } catch (Exception ignored) { }

                    // Get the specified private key or public identifier. The final condition is the legacy situation
                    // of a raw hex value for the private key seed.
                    byte[] privateSeed;
                    byte[] publicIdentifier;
                    NyzoString seedOrIdentifierString = NyzoStringEncoder.decode(split[2]);
                    if (seedOrIdentifierString instanceof NyzoStringPublicIdentifier) {
                        privateSeed = new byte[FieldByteSize.seed];
                        publicIdentifier = ((NyzoStringPublicIdentifier) seedOrIdentifierString).getIdentifier();
                    } else if (seedOrIdentifierString instanceof NyzoStringPrivateSeed) {
                        privateSeed = ((NyzoStringPrivateSeed) seedOrIdentifierString).getSeed();
                        publicIdentifier = KeyUtil.identifierForSeed(privateSeed);
                    } else {
                        privateSeed = ByteUtil.byteArrayFromHexString(split[2], FieldByteSize.seed);
                        publicIdentifier = ByteUtil.isAllZeros(privateSeed) ? new byte[FieldByteSize.identifier] :
                                KeyUtil.identifierForSeed(privateSeed);
                    }

                    boolean sentinelTransactionEnabled = false;
                    if (split.length > 3) {
                        try {
                            if (split[3].toLowerCase().equals("y")) {
                                sentinelTransactionEnabled = true;
                            }
                        } catch (Exception ignored) { }
                    }

                    if (!host.isEmpty() && port > 0 && !ByteUtil.isAllZeros(publicIdentifier)) {
                        result = new ManagedVerifier(host, port, privateSeed, publicIdentifier,
                                sentinelTransactionEnabled);
                    }
                }
            } catch (Exception ignored) { }
        }

        return result;
    }

    public void logResult(int queryResult) {
        // Store the result and advance the index.
        queryResults[queryIndex] = queryResult;
        queryIndex = (queryIndex + 1) % queryHistoryLength;
    }

    public int getQueryIndex() {
        return queryIndex;
    }

    public int[] getQueryResults() {
        return queryResults;
    }

    public boolean isQueriedLastInterval() {
        return queriedLastInterval;
    }

    public void setQueriedLastInterval(boolean queriedLastInterval) {
        this.queriedLastInterval = queriedLastInterval;
    }

    @Override
    public String toString() {
        return "[ManagedVerifier: host=" + getHost() + ", port=" + getPort() + ", id=" +
                PrintUtil.compactPrintByteArray(getIdentifier()) + "]";
    }
}
