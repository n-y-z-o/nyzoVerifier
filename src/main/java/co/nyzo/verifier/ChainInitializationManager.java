package co.nyzo.verifier;

import co.nyzo.verifier.messages.BlockRequest;
import co.nyzo.verifier.messages.BlockResponse;
import co.nyzo.verifier.messages.BootstrapResponse;
import co.nyzo.verifier.util.UpdateUtil;

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

    public static void fetchChainSection(long startBlock, long endBlock, byte[] startBlockHash) {

        while (BlockManager.highestBlockFrozen() < endBlock && !UpdateUtil.shouldTerminate()) {

            long requestBlockHeight = Math.max(startBlock, BlockManager.highestBlockFrozen() + 1);
            boolean fetchBalanceList = startBlock > BlockManager.highestBlockFrozen() + 1;

            Message message = new Message(MessageType.BlockRequest11, new BlockRequest(requestBlockHeight,
                    fetchBalanceList));
            Message.fetch(message, false, new MessageCallback() {
                @Override
                public void responseReceived(Message message) {

                    BlockResponse response = (BlockResponse) message.getContent();
                    System.out.println("got " + response.getBlocks().size() + " blocks in response, requested block " +
                            "height was " + requestBlockHeight);
                    for (Block block : response.getBlocks()) {
                        if (block.getBlockHeight() == startBlock) {
                            if (fetchBalanceList && response.getInitialBalanceList() != null) {
                                block.setBalanceList(response.getInitialBalanceList());
                                BlockManager.freezeBlock(block, startBlockHash);
                            } else {
                                BlockManager.freezeBlock(block);
                            }
                        } else {
                            BlockManager.freezeBlock(block);
                        }
                    }
                }
            });

            try {
                Thread.sleep(2000);
            } catch (Exception ignored) { }
        }
    }
}
