package co.nyzo.verifier.nyzoString;

import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.HashUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NyzoStringEncoder {

    private static final char[] characterLookup = ("0123456789" +
            "abcdefghijkmnopqrstuvwxyz" +  // all except lowercase "L"
            "ABCDEFGHIJKLMNPQRSTUVWXYZ" +  // all except uppercase "o"
            //"*+=_").toCharArray();       // old encoding, less URL-friendly
            "-.~_").toCharArray();         // see https://tools.ietf.org/html/rfc3986#section-2.3

    private static final Map<Character, Integer> characterToValueMap = new ConcurrentHashMap<>();
    static {
        for (int i = 0; i < characterLookup.length; i++) {
            characterToValueMap.put(characterLookup[i], i);
        }
    }

    private static final int headerLength = 4;

    public static String encode(NyzoString stringObject) {

        // Get the prefix array from the type and the content array from the content object.
        byte[] prefixBytes = stringObject.getType().getPrefixBytes();
        byte[] contentBytes = stringObject.getBytes();

        // Determine the length of the expanded array with the header and the checksum. The header is the type-specific
        // prefix in characters followed by a single byte that indicates the length of the content array (four bytes
        // total). The checksum is a minimum of 4 bytes and a maximum of 6 bytes, widening the expanded array so that
        // its length is divisible by 3.
        int checksumLength = 4 + (3 - (contentBytes.length + 2) % 3) % 3;
        int expandedLength = headerLength + contentBytes.length + checksumLength;

        // Create the array and add the header and the content. The first three bytes turn into the user-readable
        // prefix in the encoded string. The next byte specifies the length of the content array, and it is immediately
        // followed by the content array.
        byte[] expandedArray = new byte[expandedLength];
        ByteBuffer expandedBuffer = ByteBuffer.wrap(expandedArray);
        for (int i = 0; i < prefixBytes.length; i++) {
            expandedBuffer.put(prefixBytes[i]);
        }
        expandedBuffer.put((byte) contentBytes.length);
        expandedBuffer.put(contentBytes);

        // Compute the checksum and add the appropriate number of bytes to the end of the array.
        byte[] checksum = HashUtil.doubleSHA256(Arrays.copyOf(expandedArray, 4 + contentBytes.length));
        expandedBuffer.put(checksum, 0, checksumLength);

        // Build and return the encoded string from the expanded array.
        return encodedStringForByteArray(expandedArray);
    }

    public static NyzoString decode(String encodedString) {

        NyzoString result = null;

        try {
            // Map characters from the old encoding to the new encoding. A few characters were changed to make Nyzo
            // strings more URL-friendly.
            encodedString = encodedString.replace('*', '-').replace('+', '.').replace('=', '~');

            // Map characters that may be mistyped. Nyzo strings contain neither 'l' nor 'O'.
            encodedString = encodedString.replace('l', '1').replace('O', '0');

            // Get the type from the prefix.
            NyzoStringType type = NyzoStringType.forPrefix(encodedString.substring(0, 4));

            // If the type is valid, continue.
            if (type != null) {

                // Get the array representation of the encoded string.
                byte[] expandedArray = byteArrayForEncodedString(encodedString);

                // Get the content length from the next byte and calculate the checksum length.
                int contentLength = expandedArray[3] & 0xff;
                int checksumLength = expandedArray.length - contentLength - 4;

                // Only continue if the checksum length is valid.
                if (checksumLength >= 4 && checksumLength <= 6) {

                    // Calculate the checksum and compare it to the provided checksum. Only create the result array if
                    // the checksums match.
                    byte[] calculatedChecksum = Arrays.copyOf(HashUtil.doubleSHA256(Arrays.copyOf(expandedArray,
                            headerLength + contentLength)), checksumLength);
                    byte[] providedChecksum = Arrays.copyOfRange(expandedArray, expandedArray.length - checksumLength,
                            expandedArray.length);

                    if (ByteUtil.arraysAreEqual(calculatedChecksum, providedChecksum)) {

                        // Get the content array. This is the encoded object with the prefix, length byte, and checksum
                        // removed.
                        byte[] contentBytes = Arrays.copyOfRange(expandedArray, headerLength, expandedArray.length -
                                checksumLength);

                        // Make the object from the content array.
                        switch (type) {
                            case PrefilledData:
                                result = NyzoStringPrefilledData.fromByteBuffer(ByteBuffer.wrap(contentBytes));
                                break;
                            case PrivateSeed:
                                result = new NyzoStringPrivateSeed(contentBytes);
                                break;
                            case PublicIdentifier:
                                result = new NyzoStringPublicIdentifier(contentBytes);
                                break;
                            case Micropay:
                                result = NyzoStringMicropay.fromByteBuffer(ByteBuffer.wrap(contentBytes));
                                break;
                            case Transaction:
                                result = NyzoStringTransaction.fromByteBuffer(ByteBuffer.wrap(contentBytes));
                                break;
                        }
                    }
                }
            }
        } catch (Exception ignored) { }

        return result;
    }

    public static byte[] byteArrayForEncodedString(String encodedString) {

        int arrayLength = (encodedString.length() * 6 + 7) / 8;
        byte[] array = new byte[arrayLength];
        for (int i = 0; i < arrayLength; i++) {

            char leftCharacter = encodedString.charAt(i * 8 / 6);
            char rightCharacter = encodedString.charAt(i * 8 / 6 + 1);

            int leftValue = characterToValueMap.getOrDefault(leftCharacter, 0);
            int rightValue = characterToValueMap.getOrDefault(rightCharacter, 0);
            int bitOffset = (i * 2) % 6;
            array[i] = (byte) ((((leftValue << 6) + rightValue) >> 4 - bitOffset) & 0xff);
        }

        return array;
    }

    public static String encodedStringForByteArray(byte[] array) {

        int index = 0;
        int bitOffset = 0;
        StringBuilder encodedString = new StringBuilder();
        while (index < array.length) {

            // Get the current and next byte.
            int leftByte = array[index] & 0xff;
            int rightByte = index < array.length - 1 ? array[index + 1] & 0xff : 0;

            // Append the character for the next 6 bits in the array.
            int lookupIndex = (((leftByte << 8) + rightByte) >> (10 - bitOffset)) & 0x3f;
            encodedString.append(characterLookup[lookupIndex]);

            // Advance forward 6 bits.
            if (bitOffset == 0) {
                bitOffset = 6;
            } else {
                index++;
                bitOffset -= 2;
            }
        }

        return encodedString.toString();
    }
}
