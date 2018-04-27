package co.nyzo.verifier;

import java.util.Arrays;
import java.util.List;

public class ByteUtil {

    public static byte[] toArray(List<Byte> list) {

        byte[] array = new byte[list.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = list.get(i);
        }

        return array;
    }

    public static byte[] dataSegment(byte[] array) {

        byte[] dataSegment;
        if (array.length > 64) {
            dataSegment = Arrays.copyOf(array, array.length - 64);
        } else {
            dataSegment = new byte[0];
        }

        return dataSegment;
    }

    public static byte[] signatureSegment(byte[] array) {

        byte[] signatureSegment;
        if (array.length > 64) {
            signatureSegment = Arrays.copyOfRange(array, array.length - 64, array.length);
        } else {
            signatureSegment = new byte[0];
        }

        return signatureSegment;
    }

    public static String arrayAsStringNoDashes(long value) {

        return String.format("%016x", value);
    }

    public static String arrayAsStringNoDashes(byte[] array) {

        return arrayAsStringNoDashes(array, 0, array.length);
    }

    public static String arrayAsStringNoDashes(byte[] array, int offset, int length) {

        StringBuilder result = new StringBuilder();
        try {
            for (int i = offset; i < offset + length; i++) {
                result.append(String.format("%02x", array[i]));
            }
        } catch (Exception ignored) { }

        return result.toString();
    }

    public static String arrayAsStringWithDashes(byte[] array) {

        StringBuilder result = new StringBuilder();
        if (array == null) {
            result.append("(null)");
        } else {
            try {
                for (int i = 0; i < array.length; i++) {
                    if (i % 8 == 7 && i < array.length - 1) {
                        result.append(String.format("%02x-", array[i]));
                    } else {
                        result.append(String.format("%02x", array[i]));
                    }
                }
            } catch (Exception ignored) { }
        }

        return result.toString();
    }


    public static String arrayAsStringWithDashesExtraWrap(byte[] array) {

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            if (i % 8 == 3) {
                result.append(String.format("%02x&#8203;", array[i]));
            } else if (i % 8 == 7 && i < array.length - 1) {
                result.append(String.format("%02x-", array[i]));
            } else {
                result.append(String.format("%02x", array[i]));
            }
        }

        return result.toString();
    }

    public static boolean arraysAreEqual(byte[] array1, byte[] array2) {

        boolean arraysAreEqual = array1.length == array2.length;
        for (int i = 0; i < array1.length && arraysAreEqual; i++) {
            if (array1[i] != array2[i]) {
                arraysAreEqual = false;
            }
        }

        return arraysAreEqual;
    }

    public static byte[] byteArrayFromHexString(String string, int length) {

        byte[] result = new byte[length];
        char[] characters = string.toLowerCase().toCharArray();
        int resultIndex = 0;
        int characterIndex = 0;
        char previousCharacter = 0;

        try {
            while (resultIndex < length && characterIndex < characters.length) {
                char character = characters[characterIndex++];
                if ((character >= '0' && character <= '9') || (character >= 'a' && character <= 'f')) {
                    if (previousCharacter == 0) {
                        previousCharacter = character;
                    } else {
                        result[resultIndex++] = (byte) Integer.parseInt(previousCharacter + "" + character, 16);
                        previousCharacter = 0;
                    }
                }
            }
        } catch (Exception ignored) { }

        return result;
    }

    public static boolean isAllZeros(byte[] array) {
        boolean isAllZeros = true;
        if (array != null) {
            for (int i = 0; i < array.length && isAllZeros; i++) {
                if (array[i] != 0) {
                    isAllZeros = false;
                }
            }
        }

        return isAllZeros;
    }
}
