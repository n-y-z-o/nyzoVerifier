package co.nyzo.verifier.util;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

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
}
