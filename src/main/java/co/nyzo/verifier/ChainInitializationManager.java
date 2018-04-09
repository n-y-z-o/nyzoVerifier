package co.nyzo.verifier;

import co.nyzo.verifier.messages.NodeJoinResponse;

import java.util.List;

public class ChainInitializationManager {

    public static void processNodeJoinResponse(NodeJoinResponse response) {

        System.out.println("the node join response has " + response.getBlocks().size() + " blocks");

        // First, try to get the Genesis block.
        List<Block> blocks = response.getBlocks();
        for (Block block : blocks) {
            if (Block.isValidGenesisBlock(block, null)) {
                BlockManager.freezeBlock(block);
                System.out.println("GOT GENESIS BLOCK!!!");
            }
        }
    }
}
