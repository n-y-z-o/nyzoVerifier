package co.nyzo.verifier;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

public class HashUtil {

    public static void main(String[] args) {

        byte[] helloBytes = "hello".getBytes(StandardCharsets.US_ASCII);
        System.out.println(ByteUtil.arrayAsStringNoDashes(singleSHA256(helloBytes)) + " (should be " +
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824)");
        System.out.println(ByteUtil.arrayAsStringNoDashes(doubleSHA256(helloBytes)) + " (should be " +
                "9595c9df90075148eb06860365df33584b75bff782a510c6cd4883a419833d50)");

        byte[] emptyBytes = new byte[0];
        System.out.println(ByteUtil.arrayAsStringNoDashes(singleSHA256(emptyBytes)) + " (should be " +
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855)");
        System.out.println(ByteUtil.arrayAsStringNoDashes(doubleSHA256(emptyBytes)) + " (should be " +
                "5df6e0e2761359d30a8275058e299fcc0381534545f55cf43e41983f5d4c9456)");
    }

    private static MessageDigest messageDigest;
    static {
        try {
            messageDigest = MessageDigest.getInstance("SHA-256");
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
    }

    public static synchronized byte[] singleSHA256(byte[] data) {

        if (data == null) {
            data = new byte[0];
        }
        return messageDigest.digest(data);
    }

    public static synchronized byte[] doubleSHA256(byte[] data) {

        return messageDigest.digest(messageDigest.digest(data));
    }

    public static long longSHA256(byte[] data) {

        byte[] sha256 = singleSHA256(data);
        ByteBuffer buffer = ByteBuffer.wrap(sha256);
        return buffer.getLong();
    }

    public static long longSHA256(byte[]... dataArgs) {

        int length = 0;
        for (byte[] data : dataArgs) {
            length += data.length;
        }

        byte[] array = new byte[length];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        for (byte[] data : dataArgs) {
            buffer.put(data);
        }

        return longSHA256(array);
    }

    public static byte[] bLongSHA256(byte[] data) {

        byte[] sha256 = singleSHA256(data);
        return Arrays.copyOf(sha256, 8);
    }

    public static byte[] bLongSHA256(byte[]... dataArgs) {

        int length = 0;
        for (byte[] data : dataArgs) {
            length += data.length;
        }

        byte[] array = new byte[length];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        for (byte[] data : dataArgs) {
            buffer.put(data);
        }

        return bLongSHA256(array);
    }

    public static byte[] byteArray(int value) {

        byte[] array = new byte[4];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.putInt(value);

        return array;
    }

    public static byte[] byteArray(long value) {

        byte[] array = new byte[8];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.putLong(value);

        return array;
    }
}
