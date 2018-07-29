package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;

public class BlockVote implements MessageObject {

    private short numberOfVotesToCancel; // the number of votes under this vote that should be cancelled; typically zero
    private long height;
    private byte[] hash;

    public BlockVote(short numberOfVotesToCancel, long height, byte[] hash) {

        this.numberOfVotesToCancel = (short) Math.min(numberOfVotesToCancel, 100);  // currently limited to 100 votes
        this.height = height;
        this.hash = hash;
    }

    public short getNumberOfVotesToCancel() {
        return numberOfVotesToCancel;
    }

    public long getHeight() {
        return height;
    }

    public byte[] getHash() {
        return hash;
    }

    @Override
    public int getByteSize() {
        return FieldByteSize.unnamedShort + FieldByteSize.blockHeight + FieldByteSize.hash;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];

        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.putShort(numberOfVotesToCancel);
        buffer.putLong(height);
        buffer.put(hash);

        return array;
    }

    public static BlockVote fromByteBuffer(ByteBuffer buffer) {

        BlockVote result = null;

        try {
            short numberOfVotesToCancel = buffer.getShort();
            long height = buffer.getLong();
            byte[] hash = new byte[FieldByteSize.hash];
            buffer.get(hash);

            result = new BlockVote(numberOfVotesToCancel, height, hash);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    public static BlockVote forHeight(long height) {

        BlockVote result = null;

        try {
            // For a frozen block, return the hash of the block. It does not matter whether we originally voted
            // for this block or another.
            if (height <= BlockManager.getFrozenEdgeHeight()) {
                Block block = BlockManager.frozenBlockForHeight(height);
                if (block != null) {
                    result = new BlockVote((short) 0, height, block.getHash());
                }
            } else {
                // For an unfrozen block, return the local vote, if present.
                result = BlockVoteManager.getLocalVoteForHeight(height);
            }
        } catch (Exception ignored) { }

        // If the result is null, create a vote for an invalid height. The object cannot be null for a
        // MissingBlockVoteResponse24 message, and an invalid vote for a valid height would nullify this verifier's
        // valid vote for that height.
        if (result == null) {
            result = new BlockVote((short) 0, -1, new byte[FieldByteSize.hash]);
        }

        return result;
    }

    @Override
    public String toString() {
        return "[BlockVote: numberOfVotesToCancel=" + getNumberOfVotesToCancel() + ", height=" + getHeight() +
                ", hash=" + PrintUtil.compactPrintByteArray(getHash()) + "]";
    }
}
