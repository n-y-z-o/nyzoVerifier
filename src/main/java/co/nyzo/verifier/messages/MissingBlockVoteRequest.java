package co.nyzo.verifier.messages;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.MessageObject;

import java.nio.ByteBuffer;

public class MissingBlockVoteRequest implements MessageObject {

    private long height;

    public MissingBlockVoteRequest(long height) {

        this.height = height;
    }

    public long getHeight() {
        return height;
    }

    @Override
    public int getByteSize() {

        return FieldByteSize.blockHeight;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.putLong(height);

        return array;
    }

    public static MissingBlockVoteRequest fromByteBuffer(ByteBuffer buffer) {

        MissingBlockVoteRequest result = null;

        try {
            long height = buffer.getLong();

            result = new MissingBlockVoteRequest(height);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }
}
