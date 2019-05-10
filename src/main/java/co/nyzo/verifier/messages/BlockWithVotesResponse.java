package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BlockWithVotesResponse implements MessageObject {

    private Block block;
    private List<BlockVote> votes;

    public BlockWithVotesResponse(long height) {

        this.block = BlockManager.frozenBlockForHeight(height);
        Map<ByteBuffer, BlockVote> votes = BlockVoteManager.votesForHeight(height);
        if (votes != null) {
            this.votes = new ArrayList<>(votes.values());
        }
    }

    public BlockWithVotesResponse(Block block, List<BlockVote> votes) {

        this.block = block;
        this.votes = votes;
    }

    public Block getBlock() {
        return block;
    }

    public List<BlockVote> getVotes() {
        return votes;
    }

    @Override
    public int getByteSize() {

        int byteSize = FieldByteSize.booleanField;  // boolean value indicating whether a block is included
        if (block != null) {
            byteSize += block.getByteSize();
        }

        // A short indicates the list size.
        byteSize += FieldByteSize.unnamedShort;

        // Each vote requires only the identifier, timestamp, message timestamp, and message signature. The hash and
        // height are redundant with the block information.
        if (votes != null) {
            byteSize += (FieldByteSize.identifier + FieldByteSize.timestamp * 2 + FieldByteSize.signature) *
                    votes.size();
        }

        return byteSize;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(block == null ? (byte) 0 : (byte) 1);
        if (block != null) {
            buffer.put(block.getBytes());
        }

        if (votes == null) {
            buffer.putShort((short) 0);
        } else {
            buffer.putShort((short) votes.size());
            for (BlockVote vote : votes) {
                buffer.put(vote.getSenderIdentifier());
                buffer.putLong(vote.getTimestamp());
                buffer.putLong(vote.getMessageTimestamp());
                buffer.put(vote.getMessageSignature());
            }
        }

        return array;
    }

    public static BlockWithVotesResponse fromByteBuffer(ByteBuffer buffer) {

        BlockWithVotesResponse result = null;

        try {
            Block block = null;
            if (buffer.get() == 1) {
                block = Block.fromByteBuffer(buffer);
            }

            List<BlockVote> votes = new ArrayList<>();
            int numberOfVotes = buffer.getShort() & 0xffff;
            if (block != null) {
                for (int i = 0; i < numberOfVotes; i++) {
                    byte[] senderIdentifier = Message.getByteArray(buffer, FieldByteSize.identifier);
                    long timestamp = buffer.getLong();
                    long messageTimestamp = buffer.getLong();
                    byte[] messageSignature = Message.getByteArray(buffer, FieldByteSize.signature);

                    BlockVote vote = new BlockVote(block.getBlockHeight(), block.getHash(), timestamp);
                    vote.setSenderIdentifier(senderIdentifier);
                    vote.setMessageTimestamp(messageTimestamp);
                    vote.setMessageSignature(messageSignature);
                    votes.add(vote);
                }
            }

            result = new BlockWithVotesResponse(block, votes);
        } catch (Exception ignored) { }

        return result;
    }

    @Override
    public String toString() {
        return "[BlockWithVotesResponse(block=" + block + ", votes=" + votes.size() + ")]";
    }
}
