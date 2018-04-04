package co.nyzo.verifier.util;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;

public class IpUtil {

    private static final byte[] publicIp = new byte[4];

    public static void main(String[] args) {
        byte[] publicIp = myPublicIp();
        System.out.println("my public IP is " + (publicIp[0] & 0xff) + "." + (publicIp[1] & 0xff) + "." +
                (publicIp[2] & 0xff) + "." + (publicIp[3] & 0xff));

        Random random = new Random();
        for (int i = 0; i < 10000000; i++) {
            byte[] address = new byte[4];
            for (int j = 0; j < 4; j++) {
                address[j] = (byte) random.nextInt(256);
            }

            int addressAsInt = addressAsInt(address);
            byte[] addressFromInt = addressFromInt(addressAsInt);
            for (int j = 0; j < 4; j++) {
                if (address[j] != addressFromInt[j]) {
                    System.out.println("mismatch at iteration " + i + ", position " + j + ": " + address[j] + " != " +
                            addressFromInt[j]);
                    System.out.println("original IP was " + addressAsString(address));
                    System.out.println("integer value was " + addressAsInt);
                    System.out.println("modified IP was " + addressAsString(addressFromInt));
                    System.exit(1);
                }
            }

        }
    }

    public static byte[] myPublicIp() {

        // If we haven't yet gotten the public IP address, try to get it now.
        if (publicIp[0] == 0x0) {

            // First, try the ec2 method, in case we are running on AWS.
            getIpFromUrl("http://169.254.169.254/latest/meta-data/public-ipv4", 2000);

            if (publicIp[0] == 0x0) {
                System.out.println("trying fallback");

                // This is the fallback method.
                getIpFromUrl("https://api.ipify.org/?format=raw", 0);
            }
        }

        return Arrays.copyOf(publicIp, 4);
    }

    private static void getIpFromUrl(String url, int timeoutMilliseconds) {

        String result = NetworkUtil.stringForUrl(url, timeoutMilliseconds);
        System.out.println("result is " + result);
        byte[] publicIpFallback = parseIp(result);
        for (int i = 0; i < 4; i++) {
            publicIp[i] = publicIpFallback[i];
        }
    }

    private static byte[] parseIp(String ip) {

        byte[] result = new byte[4];
        try {
            String[] split = ip.split("\\.");
            for (int i = 0; i < 4; i++) {
                result[i] = (byte) Integer.parseInt(split[i]);
            }
        } catch (Exception ignored) { }

        return result;
    }

    public static byte[] addressFromString(String addressString) {

        System.out.println("trying to get IP address from string " + addressString);

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

        System.out.println("IP address is " + IpUtil.addressAsString(result));

        return result;
    }

    public static String addressAsString(byte[] address) {

        return (address[0] & 0xff) + "." + (address[1] & 0xff) + "." + (address[2] & 0xff) + "." + (address[3] & 0xff);
    }

    public static int addressAsInt(byte[] address) {
        return ByteBuffer.wrap(address).getInt();
    }

    public static byte[] addressFromInt(int value) {
        byte[] address = new byte[4];
        ByteBuffer.wrap(address).putInt(value);
        return address;
    }
}
