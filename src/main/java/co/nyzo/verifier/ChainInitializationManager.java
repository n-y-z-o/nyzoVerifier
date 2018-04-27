package co.nyzo.verifier;

import co.nyzo.verifier.messages.NodeJoinResponse;

public class ChainInitializationManager {

    public static void processNodeJoinResponse(NodeJoinResponse response) {

        // First, try to get the Genesis block if we don't have one already stored.
        Block localGenesisBlock = BlockManager.frozenBlockForHeight(0L);
        Block responseGenesisBlock = response.getGenesisBlock();
        if (localGenesisBlock == null && responseGenesisBlock != null) {
            if (Block.isValidGenesisBlock(responseGenesisBlock, null)) {
                System.out.println("GOT GENESIS BLOCK!!!");
                BlockManager.freezeBlock(responseGenesisBlock);
            }
        }
    }
}
