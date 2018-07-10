package co.nyzo.verifier;

import co.nyzo.verifier.util.PrintUtil;

public class TrustedEntryPoint {

    private String host;
    private int port;

    private TrustedEntryPoint(String host, int port) {

        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public static TrustedEntryPoint fromString(String value) {

        TrustedEntryPoint result = null;
        if (value != null && !value.isEmpty()) {
            try {
                String[] split = value.split(":");
                if (split.length == 2) {
                    String host = split[0];
                    int port = -1;
                    try {
                        port = Integer.parseInt(split[1]);
                    } catch (Exception ignored) {
                    }
                    if (!host.isEmpty() && port > 0) {
                        result = new TrustedEntryPoint(host, port);
                    }
                }
            } catch (Exception ignored) { }
        }

        return result;
    }

    @Override
    public String toString() {
        return "[TrustedEntryPoint: host=" + getHost() + ", port=" + getPort() + "]";
    }
}
