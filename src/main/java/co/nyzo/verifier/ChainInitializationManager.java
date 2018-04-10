package co.nyzo.verifier;

import co.nyzo.verifier.messages.NodeJoinResponse;

import java.util.List;

public class ChainInitializationManager {

    public static void processNodeJoinResponse(NodeJoinResponse response) {

        System.out.println("the node join response has " + response.getBlockHashes().size() + " hashes");

        // First, try to get the Genesis block if we don't have one already stored.
        Block localGenesisBlock = BlockManager.frozenBlockForHeight(0L);
        Block responseGenesisBlock = response.getGenesisBlock();
        if (localGenesisBlock == null && responseGenesisBlock != null) {
            if (Block.isValidGenesisBlock(responseGenesisBlock, null)) {
                // Set the balance list. It is not transmitted because it is easy to rebuild.
                responseGenesisBlock.setBalanceList(Block.balanceListForNextBlock(null,
                        responseGenesisBlock.getTransactions(), responseGenesisBlock.getVerifierIdentifier()));
                BlockManager.freezeBlock(responseGenesisBlock);
                System.out.println("GOT GENESIS BLOCK!!!");
            }
        }
    }
}
