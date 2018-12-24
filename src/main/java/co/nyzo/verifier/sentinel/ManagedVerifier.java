package co.nyzo.verifier.sentinel;

import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.KeyUtil;
import co.nyzo.verifier.util.PrintUtil;

public class ManagedVerifier {

    private String host;
    private int port;
    private byte[] seed;
    private byte[] identifier;
    private boolean sentinelTransactionEnabled;

    private ManagedVerifier(String host, int port, byte[] seed, boolean sentinelTransactionEnabled) {

        this.host = host;
        this.port = port;
        this.seed = seed;
        this.identifier = KeyUtil.identifierForSeed(seed);
        this.sentinelTransactionEnabled = sentinelTransactionEnabled;
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

    @Override
    public String toString() {
        return "[ManagedVerifier: host=" + getHost() + ", port=" + getPort() + ", id=" +
                PrintUtil.compactPrintByteArray(getIdentifier()) + "]";
    }
}
