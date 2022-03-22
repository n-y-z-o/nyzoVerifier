package co.nyzo.verifier.client;

import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.nyzoString.NyzoString;
import co.nyzo.verifier.nyzoString.NyzoStringEncoder;
import co.nyzo.verifier.nyzoString.NyzoStringPublicIdentifier;

import java.nio.charset.StandardCharsets;

public class ClientArgumentUtil {

    public static int getInteger(String argumentValue, int defaultValue) {
        return getInteger(argumentValue, defaultValue, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public static int getInteger(String argumentValue, int defaultValue, int minimumValue, int maximumValue) {
        int result;
        try {
            result = Integer.parseInt(argumentValue);
        } catch (Exception e) {
            result = defaultValue;
        }

        return Math.max(minimumValue, Math.min(result, maximumValue));
    }

    public static long getLong(String argumentValue, long defaultValue) {
        return getLong(argumentValue, defaultValue, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    public static long getLong(String argumentValue, long defaultValue, long minimumValue, long maximumValue) {
        long result;
        try {
            result = Long.parseLong(argumentValue);
        } catch (Exception e) {
            result = defaultValue;
        }

        return Math.max(minimumValue, Math.min(result, maximumValue));
    }

    public static byte[] getSenderData(String argumentValue) {
        byte[] senderData;
        if (ClientTransactionUtil.isNormalizedSenderDataString(argumentValue)) {
            senderData = ClientTransactionUtil.bytesFromNormalizedSenderDataString(argumentValue);
        } else {
            senderData = argumentValue.getBytes(StandardCharsets.UTF_8);
        }

        // Limit to the maximum allowable length.
        if (senderData.length > FieldByteSize.maximumSenderDataLength) {
            byte[] truncatedData = new byte[FieldByteSize.maximumSenderDataLength];
            System.arraycopy(senderData, 0, truncatedData, 0, FieldByteSize.maximumSenderDataLength);
            senderData = truncatedData;
        }

        return senderData;
    }

    public static NyzoStringPublicIdentifier getPublicIdentifier(String argumentValue) {

        NyzoString decoded = NyzoStringEncoder.decode(argumentValue);
        NyzoStringPublicIdentifier result;
        if (decoded instanceof NyzoStringPublicIdentifier) {
            result = (NyzoStringPublicIdentifier) decoded;
        } else {
            result = new NyzoStringPublicIdentifier(ByteUtil.byteArrayFromHexString(argumentValue,
                    FieldByteSize.identifier));
        }

        return result;
    }
}
