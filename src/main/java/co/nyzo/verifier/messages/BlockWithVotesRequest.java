package co.nyzo.verifier.messages;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.MessageObject;

import java.nio.ByteBuffer;

public class BlockWithVotesRequest implements MessageObject {

    private long height;

    public BlockWithVotesRequest(long height) {

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

    public static BlockWithVotesRequest fromByteBuffer(ByteBuffer buffer) {

        BlockWithVotesRequest result = null;

        try {
            long height = buffer.getLong();
            result = new BlockWithVotesRequest(height);
        } catch (Exception ignored) { }

        return result;
    }
}
