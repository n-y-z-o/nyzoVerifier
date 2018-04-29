package co.nyzo.verifier;

import co.nyzo.verifier.messages.NodeJoinResponse;

import java.util.HashMap;
import java.util.Map;

public class ChainInitializationManager {

    private static final Map<Long, FrozenBlockVoteTally> hashVotes = new HashMap<>();

    public static synchronized void processNodeJoinResponse(NodeJoinResponse response) {

        System.out.println("processing node-join response");

        // First, try to get the Genesis block if we don't have one already stored.
        Block localGenesisBlock = BlockManager.frozenBlockForHeight(0L);
        Block responseGenesisBlock = response.getGenesisBlock();
        if (localGenesisBlock == null && responseGenesisBlock != null) {
            if (Block.isValidGenesisBlock(responseGenesisBlock, null)) {
                System.out.println("GOT GENESIS BLOCK FROM NETWORK!!!");
                BlockManager.freezeBlock(responseGenesisBlock);
            }
        }

        // Accumulate votes for the hashes.
        int numberOfHashes = Math.min(response.getBlockHashes().size(), response.getBlockHeights().size());
        for (int i = 0; i < numberOfHashes; i++) {
            long blockHeight = response.getBlockHeights().get(i);
            FrozenBlockVoteTally voteTally = hashVotes.get(blockHeight);
            if (voteTally == null) {
                voteTally = new FrozenBlockVoteTally();
                hashVotes.put(blockHeight, voteTally);
            }

            // If consensus is reached on a hash, try to get the block from the network.
            byte[] hash = response.getBlockHashes().get(i);
            boolean consensus = voteTally.vote(response.getBlockHashes().get(i));
            if (consensus && blockHeight > BlockManager.highestBlockFrozen()) {
                System.out.println("*** need to get frozen block at height " + blockHeight + " from network ***");
                getBlockFromNetwork(blockHeight, hash);
            }
        }
    }

    private static void getBlockFromNetwork(long blockHeight, byte[] hash) {


    }
}
