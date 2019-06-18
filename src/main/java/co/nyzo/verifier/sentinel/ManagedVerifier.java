package co.nyzo.verifier.sentinel;

import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.KeyUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.util.Arrays;

public class ManagedVerifier {

    private String host;
    private int port;
    private byte[] seed;
    private byte[] identifier;
    private boolean sentinelTransactionEnabled;

    // These are used to track the health of the verifier.
    private int[] queryResults;
    private int queryIndex;
    private boolean queriedLastInterval;

    public static final int queryResultNotYetQueriedValue = -2;
    public static final int queryResultErrorValue = -1;
    public static final int queryHistoryLength = 10;

    private ManagedVerifier(String host, int port, byte[] seed, boolean sentinelTransactionEnabled) {

        this.host = host;
        this.port = port;
        this.seed = seed;
        this.identifier = KeyUtil.identifierForSeed(seed);
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

                    byte[] privateSeed = ByteUtil.byteArrayFromHexString(split[2], FieldByteSize.seed);

                    boolean sentinelTransactionEnabled = false;
                    if (split.length > 3) {
                        try {
                            if (split[3].toLowerCase().equals("y")) {
                                sentinelTransactionEnabled = true;
                            }
                        } catch (Exception ignored) { }
                    }

                    if (!host.isEmpty() && port > 0 && !ByteUtil.isAllZeros(privateSeed)) {
                        result = new ManagedVerifier(host, port, privateSeed, sentinelTransactionEnabled);
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
