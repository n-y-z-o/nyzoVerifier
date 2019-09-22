package co.nyzo.verifier.messages;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.Message;
import co.nyzo.verifier.MessageObject;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;

public class MinimalBlock implements MessageObject {

    private long verificationTimestamp;
    private byte[] signature;

    public MinimalBlock(long verificationTimestamp, byte[] signature) {
        this.verificationTimestamp = verificationTimestamp;
        this.signature = signature;
    }

    public long getVerificationTimestamp() {
        return verificationTimestamp;
    }

    public byte[] getSignature() {
        return signature;
    }

    @Override
    public int getByteSize() {
        return FieldByteSize.timestamp + FieldByteSize.signature;
    }

    @Override
    public byte[] getBytes() {
        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.putLong(verificationTimestamp);
        buffer.put(signature);

        return array;
    }

    public static MinimalBlock fromByteBuffer(ByteBuffer buffer) {

        MinimalBlock result = null;
        try {
            long verificationTimestamp = buffer.getLong();
            byte[] signature = Message.getByteArray(buffer, FieldByteSize.signature);
            result = new MinimalBlock(verificationTimestamp, signature);
        } catch (Exception ignored) {
        }

        return result;
    }

    @Override
    public String toString() {
        return "[MinimalBlock(verificationTimestamp=" + verificationTimestamp + ",signature=" +
                PrintUtil.compactPrintByteArray(signature) + ")]";
    }
}
