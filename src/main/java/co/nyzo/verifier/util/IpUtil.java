package co.nyzo.verifier.util;

public class IpUtil {

    public static byte[] addressFromString(String addressString) {

        addressString = addressString + ":";

        byte[] result = null;
        try {
            byte[] address = new byte[4];
            StringBuilder currentValue = new StringBuilder();
            char[] characters = addressString.toCharArray();
            int addressIndex = 0;
            for (int i = 0; i < characters.length && addressIndex < 4; i++) {
                char character = characters[i];
                if (character >= '0' && character <= '9') {
                    currentValue.append(character);
                } else if (character == '.' || character == ':') {
                    address[addressIndex++] = (byte) Integer.parseInt(currentValue.toString());
                    currentValue = new StringBuilder();
                    if (character == ':') {
                        addressIndex = 4;
                    }
                }
            }

            result = address;

        } catch (Exception ignored) { }

        return result;
    }

    public static String addressAsString(byte[] address) {

        return (address[0] & 0xff) + "." + (address[1] & 0xff) + "." + (address[2] & 0xff) + "." + (address[3] & 0xff);
    }

    public static boolean isPrivate(byte[] address) {

        // 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16
        return address[0] == 10 || (address[0] == (byte) 172 && (address[1] & 0xf0) == 16) ||
                (address[0] == (byte) 192 && address[1] == (byte) 168);
    }

    public static boolean isValidAddress(String addressString) {

        // This method ensures that the address string provided represents, according to reasonable interpretation, a
        // valid IP address. The port value is not required and need not be a valid port. Non-relevant characters are
        // ignored.

        // Some examples. See IpUtilTest for demonstration.
        // "0.0.0.0" (valid)
        // "0.0.0.0:" (valid)
        // "0.0.0.0:65354" (valid, even though the port is an invalid value)
        // "1.0.0.256" (invalid due to last byte)
        // "1.0.0.0:nyzo rocks" (valid, even though port is non-numerical)
        // "1.0.0.0 blarg" (valid despite extraneous characters)
        // "1.2.3.4" (valid)
        // "1.2.3" (not valid due to missing byte)
        // "1a.2b.3nyzo.4" (valid despite extraneous characters)
        //  "my IP address is 192.168.0.1" (valid despite extraneous characters)

        // Add a colon to the end to ensure the port delimiter is present even if a port is not provided.
        addressString = addressString + ":";

        // For consistency, this uses a similar logic to the addressFromString() method.
        boolean[] isValid = new boolean[4];
        try {
            StringBuilder currentValue = new StringBuilder();
            char[] characters = addressString.toCharArray();
            int addressIndex = 0;
            for (int i = 0; i < characters.length && addressIndex < 4; i++) {
                char character = characters[i];
                if (character >= '0' && character <= '9') {
                    currentValue.append(character);
                } else if (character == '.' || character == ':') {
                    int value = Integer.parseInt(currentValue.toString());
                    if (value >= 0 && value <= 255) {
                        isValid[addressIndex++] = true;
                    }
                    currentValue = new StringBuilder();
                    if (character == ':') {
                        addressIndex = 4;
                    }
                }
            }
        } catch (Exception ignored) { }

        return isValid[0] && isValid[1] && isValid[2] && isValid[3];
    }
}
