package co.nyzo.verifier.messages;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.MessageObject;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class HighestBlockFrozenResponse implements MessageObject {

    private long blockHeight;

    public HighestBlockFrozenResponse(long blockHeight) {
        this.blockHeight = blockHeight;
    }

    public long getBlockHeight() {
        return blockHeight;
    }

    @Override
    public int getByteSize() {
        return FieldByteSize.blockHeight;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.putLong(blockHeight);

        return array;
    }

    public static HighestBlockFrozenResponse fromByteBuffer(ByteBuffer buffer) {

        HighestBlockFrozenResponse result = null;

        try {
            long blockHeight = buffer.getLong();

            result = new HighestBlockFrozenResponse(blockHeight);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    @Override
    public String toString() {
        return "[HighestBlockFrozenResponse(" + blockHeight + ")]";
    }
}
