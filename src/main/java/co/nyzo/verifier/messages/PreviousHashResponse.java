package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;

public class PreviousHashResponse implements MessageObject {

    private long height;
    private byte[] hash;

    public PreviousHashResponse() {
        height = BlockManager.getFrozenEdgeHeight();
        hash = BlockManager.frozenBlockForHeight(height).getHash();
    }

    public PreviousHashResponse(long height, byte[] hash) {
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

        byte[] result = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(result);
        buffer.putLong(height);
        buffer.put(hash);

        return result;
    }

    public static PreviousHashResponse fromByteBuffer(ByteBuffer buffer) {

        PreviousHashResponse result = null;

        try {
            long height = buffer.getLong();
            byte[] hash = new byte[FieldByteSize.hash];
            buffer.get(hash);

            result = new PreviousHashResponse(height, hash);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    @Override
    public String toString() {
        return "[PreviousHashResponse(height=" + height + ", hash=" + PrintUtil.compactPrintByteArray(hash) + "]";
    }
}
