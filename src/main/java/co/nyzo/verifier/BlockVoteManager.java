package co.nyzo.verifier;

import co.nyzo.verifier.messages.BlockVote;
import co.nyzo.verifier.messages.StatusResponse;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class BlockVoteManager {

    // The local votes map is redundant, but it is a simple and efficient way to store local votes for responding to
    // node-join messages.
    private static final Map<Long, byte[]> localVotes = new HashMap<>();
    private static final Map<Long, Map<ByteBuffer, ByteBuffer>> voteMap = new HashMap<>();

    public static synchronized void registerVote(byte[] identifier, BlockVote vote, boolean isLocalVote) {

        // Register the vote. The map ensures that each identifier only gets one vote. Some of the votes may not count.
        // Votes are only counted for verifiers in the previous cycle.
        long height = vote.getHeight();
        if (height < BlockManager.openEdgeHeight(true)) {
            Map<ByteBuffer, ByteBuffer> votesForHeight = voteMap.get(height);
            if (votesForHeight == null) {
                votesForHeight = new HashMap<>();
                voteMap.put(height, votesForHeight);
            }
            votesForHeight.put(ByteBuffer.wrap(identifier), ByteBuffer.wrap(vote.getHash()));
        }

        if (isLocalVote) {
            localVotes.put(vote.getHeight(), vote.getHash());
        }
    }

    public static synchronized void removeOldVotes() {

        Set<Long> heights = new HashSet<>(voteMap.keySet());
        long frozenEdgeHeight = BlockManager.frozenEdgeHeight();
        for (long height : heights) {
            if (height <= frozenEdgeHeight) {
                voteMap.remove(height);
                localVotes.remove(height);
            }
        }
    }

    // TODO: this method is for testing and will likely be removed before release
    public static synchronized String votesAtHeight(long height) {

        int numberOfVotes = 0;
        int maximumVotes = 0;
        Map<ByteBuffer, ByteBuffer> votesForHeight = voteMap.get(height);
        if (votesForHeight != null) {
            numberOfVotes = votesForHeight.size();

            Map<ByteBuffer, Integer> voteCounts = new HashMap<>();
            for (ByteBuffer byteBuffer : votesForHeight.values()) {
                Integer count = voteCounts.get(byteBuffer);
                if (count == null) {
                    count = 1;
                } else {
                    count++;
                }
                voteCounts.put(byteBuffer, count);
                maximumVotes = Math.max(maximumVotes, count);
            }
        }

        return numberOfVotes + "(" + maximumVotes + ")";
    }

    public static synchronized byte[] winningHashForHeight(long height) {

        byte[] winningHash = null;
        Map<ByteBuffer, ByteBuffer> votesForHeight = voteMap.get(height);
        if (votesForHeight != null) {

            Map<ByteBuffer, Integer> votesPerHash = new HashMap<>();
            Set<ByteBuffer> votingVerifiers = BlockManager.verifiersInPreviousCycle();

            // This is a work-around for the Genesis cycle only. This is not especially robust, but it does not matter,
            // because it will only be used for the first cycle at the beginning of the block chain.
            if (BlockManager.inGenesisCycle()) {
                votingVerifiers.clear();
                for (Node node : NodeManager.getMesh()) {
                    votingVerifiers.add(ByteBuffer.wrap(node.getIdentifier()));
                }
            }

            // Build the vote map.
            for (ByteBuffer identifier : votesForHeight.keySet()) {
                if (votingVerifiers.contains(identifier)) {
                    ByteBuffer hash = votesForHeight.get(identifier);
                    Integer votesForHash = votesPerHash.get(hash);
                    if (votesForHash == null) {
                        votesPerHash.put(hash, 1);
                    } else {
                        votesPerHash.put(hash, votesForHash + 1);
                    }
                }
            }

            // Check the vote totals to see if any block should be frozen.
            long threshold = votingVerifiers.size() * 3L / 4L;
            int maximumVotes = 0;
            for (ByteBuffer hash : votesPerHash.keySet()) {
                maximumVotes = Math.max(maximumVotes, votesPerHash.get(hash));
                if (votesPerHash.get(hash) > threshold) {
                    winningHash = hash.array();
                }
            }

            if (height == BlockManager.frozenEdgeHeight() + 1L) {
                StatusResponse.setField("vote", "m=" + maximumVotes + ", t=" + threshold + ", " +
                        PrintUtil.compactPrintByteArray(winningHash) + ", n=" + NodeManager.getMesh().size() +
                        ", nv=" + votingVerifiers.size() + ", h=" + height);
            }
            StatusResponse.setField("in Genesis cycle", BlockManager.inGenesisCycle() + "");
        }

        return winningHash;
    }

    public static synchronized List<BlockVote> getLocalVotes() {

        List<BlockVote> votes = new ArrayList<>();
        for (Long height : localVotes.keySet()) {
            votes.add(new BlockVote(height, localVotes.get(height)));
        }

        return votes;
    }
}
