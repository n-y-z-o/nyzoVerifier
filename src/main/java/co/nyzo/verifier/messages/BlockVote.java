package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;

public class BlockVote implements MessageObject {

    private final long height;
    private final byte[] hash;
    private final long timestamp;  // to prevent replay attacks of old votes (actually, unnecessary due to message timestamp)
    private long receiptTimestamp;  // local-only field used to ensure minimum spacing between votes
    private byte[] senderIdentifier;  // sender of the message for BlockWithVotesResponse
    private long messageTimestamp;  // timestamp of the message for BlockWithVotesResponse
    private byte[] messageSignature;  // signature of the message for BlockWithVotesResponse

    public BlockVote(long height, byte[] hash, long timestamp) {

        this.height = height;
        this.hash = hash;
        this.timestamp = timestamp;
    }

    public long getHeight() {
        return height;
    }

    public byte[] getHash() {
        return hash;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getReceiptTimestamp() {
        return receiptTimestamp;
    }

    public void setReceiptTimestamp(long receiptTimestamp) {
        this.receiptTimestamp = receiptTimestamp;
    }

    public byte[] getSenderIdentifier() {
        return senderIdentifier;
    }

    public void setSenderIdentifier(byte[] senderIdentifier) {
        this.senderIdentifier = senderIdentifier;
    }

    public long getMessageTimestamp() {
        return messageTimestamp;
    }

    public void setMessageTimestamp(long messageTimestamp) {
        this.messageTimestamp = messageTimestamp;
    }

    public byte[] getMessageSignature() {
        return messageSignature;
    }

    public void setMessageSignature(byte[] messageSignature) {
        this.messageSignature = messageSignature;
    }

    @Override
    public int getByteSize() {
        return FieldByteSize.blockHeight + FieldByteSize.hash + FieldByteSize.timestamp;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];

        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.putLong(height);
        buffer.put(hash);
        buffer.putLong(timestamp);

        return array;
    }

    public static BlockVote fromByteBuffer(ByteBuffer buffer) {

        BlockVote result = null;

        try {
            long height = buffer.getLong();
            byte[] hash = new byte[FieldByteSize.hash];
            buffer.get(hash);
            long timestamp = buffer.getLong();

            result = new BlockVote(height, hash, timestamp);

        } catch (Exception ignored) { }

        return result;
    }

    public static BlockVote forHeight(long height) {

        BlockVote result = null;

        try {
            // For a frozen block, return the hash of the block. If does not matter what our final vote was.
            if (height <= BlockManager.getFrozenEdgeHeight()) {
                Block block = BlockManager.frozenBlockForHeight(height);
                if (block != null) {
                    result = new BlockVote(height, block.getHash(), System.currentTimeMillis());
                }
            } else {
                // For an unfrozen block, return the local vote, if present.
                byte[] hash = BlockVoteManager.getLocalVoteForHeight(height);
                if (hash != null) {
                    result = new BlockVote(height, hash, System.currentTimeMillis());
                }
            }
        } catch (Exception ignored) { }

        // If the result is null, vote with an empty hash.
        if (result == null) {
            result = new BlockVote(height, new byte[FieldByteSize.hash], System.currentTimeMillis());
        }

        return result;
    }

    @Override
    public String toString() {
        return "[BlockVote: height=" + getHeight() + ", hash=" + PrintUtil.compactPrintByteArray(getHash()) + "]";
    }
}
