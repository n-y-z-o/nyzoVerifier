package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;

public class BlockVote implements MessageObject {

    private long height;
    private byte[] hash;
    private short numberOfVotesToCancel;  // the number of votes under this vote that should be cancelled; typically
                                          // zero
    private short numberOfVotesToSave;  // the number of votes to save between this vote and the first cancelled vote

    public BlockVote(long height, byte[] hash, short numberOfVotesToCancel, short numberOfVotesToSave) {

        this.height = height;
        this.hash = hash;
        this.numberOfVotesToCancel = (short) Math.min(numberOfVotesToCancel, 100);  // currently limited to 100 votes
        this.numberOfVotesToSave = (short) Math.min(numberOfVotesToSave, 100);  // also currently limited to 100 votes
    }

    public long getHeight() {
        return height;
    }

    public byte[] getHash() {
        return hash;
    }

    public short getNumberOfVotesToCancel() {
        return numberOfVotesToCancel;
    }

    public short getNumberOfVotesToSave() {
        return numberOfVotesToSave;
    }

    @Override
    public int getByteSize() {
        return FieldByteSize.blockHeight + FieldByteSize.hash + FieldByteSize.unnamedShort +
                (numberOfVotesToCancel > 0 ? FieldByteSize.unnamedShort : 0);
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];

        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.putLong(height);
        buffer.put(hash);
        buffer.putShort(numberOfVotesToCancel);
        if (numberOfVotesToCancel > 0) {
            buffer.putShort(numberOfVotesToSave);
        }

        return array;
    }

    public static BlockVote fromByteBuffer(ByteBuffer buffer) {

        BlockVote result = null;

        try {
            long height = buffer.getLong();
            byte[] hash = new byte[FieldByteSize.hash];
            buffer.get(hash);
            short numberOfVotesToCancel = buffer.getShort();
            short numberOfVotesToSave = numberOfVotesToCancel > 0 ? buffer.getShort() : 0;  // only provided if
                                                                                            // numberOfVotesToCancel
                                                                                            // is non-zero
            result = new BlockVote(height, hash, numberOfVotesToCancel, numberOfVotesToSave);

        } catch (Exception ignored) { }

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
                    result = new BlockVote(height, block.getHash(), (short) 0, (short) 0);
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
            result = new BlockVote(-1, new byte[FieldByteSize.hash], (short) 0, (short) 0);
        }

        return result;
    }

    @Override
    public String toString() {
        return "[BlockVote: height=" + getHeight() + ", hash=" + PrintUtil.compactPrintByteArray(getHash()) +
                ", nCancel=" + numberOfVotesToCancel + ", nSave=" + numberOfVotesToSave + "]";
    }
}
