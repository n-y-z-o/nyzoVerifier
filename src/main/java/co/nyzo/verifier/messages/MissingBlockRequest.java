package co.nyzo.verifier.messages;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.MessageObject;

import java.nio.ByteBuffer;

public class MissingBlockRequest implements MessageObject {

    private final long height;
    private final byte[] hash;

    public MissingBlockRequest(long height, byte[] hash) {

        this.height = height;
        this.hash = hash;
    }

    public long getHeight() {
        return height;
    }

    public byte[] getHash() {
        return hash;
    }

    @Override
    public int getByteSize() {

        return FieldByteSize.blockHeight + FieldByteSize.hash;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.putLong(height);
        buffer.put(hash);

        return array;
    }

    public static MissingBlockRequest fromByteBuffer(ByteBuffer buffer) {

        MissingBlockRequest result = null;

        try {
            long height = buffer.getLong();
            byte[] hash = new byte[FieldByteSize.hash];
            buffer.get(hash);

            result = new MissingBlockRequest(height, hash);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }
}
