package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;

public class BlockVote implements MessageObject {

    private long height;
    private byte[] hash;

    public BlockVote(long height, byte[] hash) {

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

    public static BlockVote fromByteBuffer(ByteBuffer buffer) {

        BlockVote result = null;

        try {
            long height = buffer.getLong();
            byte[] hash = new byte[FieldByteSize.hash];
            buffer.get(hash);

            result = new BlockVote(height, hash);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    public static BlockVote forHeight(long height) {

        BlockVote result = null;

        try {
            if (height <= BlockManager.getFrozenEdgeHeight()) {
                Block block = BlockManager.frozenBlockForHeight(height);
                if (block != null) {
                    result = new BlockVote(height, block.getHash());
                }
            } else {
                result = BlockVoteManager.getLocalVoteForHeight(height);
            }
        } catch (Exception ignored) { }

        return result;
    }

    @Override
    public String toString() {
        return "[BlockVote: height=" + getHeight() + ", hash=" + PrintUtil.compactPrintByteArray(getHash()) + "]";
    }
}
