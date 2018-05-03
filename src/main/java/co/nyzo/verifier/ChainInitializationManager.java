package co.nyzo.verifier;

import co.nyzo.verifier.messages.BootstrapResponse;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class ChainInitializationManager {

    private static final Map<Long, FrozenBlockVoteTally> hashVotes = new HashMap<>();

    public static synchronized void processBootstrapResponseMessage(Message message) {

        System.out.println("processing node-join response");
        BootstrapResponse response = (BootstrapResponse) message.getContent();

        // Accumulate votes for the hashes.
        int numberOfHashes = response.getFrozenBlockHashes().size();
        for (int i = 0; i < numberOfHashes; i++) {
            long blockHeight = response.getFirstHashHeight() + i;
            FrozenBlockVoteTally voteTally = hashVotes.get(blockHeight);
            if (voteTally == null) {
                voteTally = new FrozenBlockVoteTally(blockHeight);
                hashVotes.put(blockHeight, voteTally);
            }

            byte[] hash = response.getFrozenBlockHashes().get(i);
            long determinationHeight = response.getDiscontinuityDeterminationHeights().get(i);
            voteTally.vote(message.getSourceNodeIdentifier(), hash, determinationHeight);
        }
    }

    public static synchronized long frozenEdgeHeight(byte[] frozenEdgeHash, AtomicLong determinationHeight) {

        // Determine the maximum number of votes we have at any level. This determines our consensus threshold.
        int maximumVotesAtAnyLevel = 0;
        for (FrozenBlockVoteTally tally : hashVotes.values()) {
            maximumVotesAtAnyLevel = Math.max(maximumVotesAtAnyLevel, tally.totalVotes());
        }

        // Determine the highest level at which consensus has been reached.
        long maximumConsensusHeight = -1;
        if (maximumVotesAtAnyLevel > 0) {
            for (long height : hashVotes.keySet()) {
                if (height > maximumConsensusHeight) {
                    FrozenBlockVoteTally tally = hashVotes.get(height);
                    if (tally.votesForWinner(frozenEdgeHash, determinationHeight) > maximumVotesAtAnyLevel / 2) {
                        maximumConsensusHeight = height;
                    }
                }
            }
        }

        return maximumConsensusHeight;
    }

    public static void fetchChainToHeight(long consensusFrozenEdge) {

        long localFrozenEdge = BlockManager.highestBlockFrozen();

        List<Block> blocks = new ArrayList<>();
        /*
        while (blocks.isEmpty() || blocks.get(blocks.size() - 1).getCycleInformation() == null) {

            List<Block> blocksFromNetwork = new ArrayList<>();


        }*/
    }
}
