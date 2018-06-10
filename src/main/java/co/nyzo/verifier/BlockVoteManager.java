package co.nyzo.verifier;

import co.nyzo.verifier.messages.BlockVote;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BlockVoteManager {

    private static Map<Long, Map<ByteBuffer, ByteBuffer>> voteMap = new HashMap<>();

    public static synchronized void registerVote(byte[] identifier, BlockVote vote) {

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
    }

    public static synchronized void removeOldVotes() {

        Set<Long> heights = new HashSet<>(voteMap.keySet());
        long frozenEdgeHeight = BlockManager.frozenEdgeHeight();
        for (long height : heights) {
            if (height <= frozenEdgeHeight) {
                voteMap.remove(height);
            }
        }
    }

    // This method is for testing and will likely be removed before release.
    public static int numberOfVotesAtHeight(long height) {

        int numberOfVotes = 0;
        Map<ByteBuffer, ByteBuffer> votesForHeight = voteMap.get(height);
        if (votesForHeight != null) {
            numberOfVotes = votesForHeight.size();
        }

        return numberOfVotes;
    }
}
