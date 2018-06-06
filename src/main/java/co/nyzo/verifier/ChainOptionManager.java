package co.nyzo.verifier;

import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class ChainOptionManager {

    // TODO: make sure we handle all of these cases properly
    // -- duplicate blocks (same signature)
    // -- two blocks at the same height from same verifier that build off different blocks
    // -- two blocks at the same height from same verifier that build off the same block (verifiers should not do this)

    private static Map<Long, List<Block>> unfrozenBlocks = new HashMap<>();

    public static synchronized boolean registerBlock(Block block) {

        boolean shouldForwardBlock = false;

        long leadingEdgeHeight = leadingEdgeHeight();
        long highestBlockFrozen = BlockManager.highestBlockFrozen();

        // Reject all blocks with invalid signatures and all those at or behind the frozen edge.
        if (!block.signatureIsValid() || block.getBlockHeight() <= highestBlockFrozen) {
            block = null;
        }

        if (block != null && block.getBlockHeight() > highestBlockFrozen) {

            // Get the list of the blocks at this height.
            List<Block> blocksAtHeight = unfrozenBlocks.get(block.getBlockHeight());
            if (blocksAtHeight == null) {
                blocksAtHeight = new ArrayList<>();
                unfrozenBlocks.put(block.getBlockHeight(), blocksAtHeight);
            }

            // Check if the block is a simple duplicate (same signature).
            boolean alreadyContainsBlock = false;
            for (int i = 0; i < blocksAtHeight.size() && !alreadyContainsBlock; i++) {
                if (ByteUtil.arraysAreEqual(blocksAtHeight.get(i).getVerifierSignature(),
                        block.getVerifierSignature())) {
                    alreadyContainsBlock = true;
                }
            }

            // Check if the block is a duplicate verifier on the same previous block.
            boolean alreadyContainsVerifierOnSameChain = false;
            if (!alreadyContainsBlock) {
                for (int i = 0; i < blocksAtHeight.size() && !alreadyContainsVerifierOnSameChain; i++) {
                    if (ByteUtil.arraysAreEqual(blocksAtHeight.get(i).getVerifierIdentifier(),
                            block.getVerifierIdentifier()) &&
                            ByteUtil.arraysAreEqual(blocksAtHeight.get(i).getPreviousBlockHash(),
                                    block.getPreviousBlockHash())) {
                        alreadyContainsVerifierOnSameChain = true;
                    }
                }
            }

            if (!alreadyContainsBlock && !alreadyContainsVerifierOnSameChain) {
                blocksAtHeight.add(block);
                shouldForwardBlock = true;
            }
        }

        return shouldForwardBlock;
    }

    public static synchronized void removeAbandonedChains() {

        // All blocks that do not extend to within 4 back of the leading edge can be removed.
        long leadingEdgeHeight = leadingEdgeHeight();
        long frozenEdgeHeight = BlockManager.highestBlockFrozen();
        for (long height = leadingEdgeHeight - 5; height >= frozenEdgeHeight + 1; height--) {
            List<Block> blocksAtHeight = unfrozenBlocks.get(height);
            if (blocksAtHeight != null) {
                for (int i = blocksAtHeight.size() - 1; i >= 0; i--) {
                    Block block = blocksAtHeight.get(i);
                    long chainHeight = chainHeightForBlock(block);
                    if (chainHeight < leadingEdgeHeight - 4) {
                        blocksAtHeight.remove(block);
                    }
                }
            }
        }
    }

    private static synchronized long chainHeightForBlock(Block block) {

        long height = block.getBlockHeight();
        long heightToCheck = height + 1;
        byte[] hash = block.getHash();
        boolean foundBreak = false;
        while (!foundBreak) {
            List<Block> blocks = unfrozenBlocks.get(heightToCheck);
            boolean foundHash = false;
            if (blocks != null) {
                for (int i = 0; i < blocks.size() && !foundHash; i++) {
                    Block blockToCheck = blocks.get(i);
                    if (ByteUtil.arraysAreEqual(blockToCheck.getPreviousBlockHash(), hash)) {
                        foundHash = true;
                        height = heightToCheck;
                        heightToCheck = height + 1;
                        hash = blockToCheck.getHash();
                    }
                }
            }

            if (!foundHash) {
                foundBreak = true;
            }
        }

        return height;
    }

    public static synchronized void freezeBlocks() {

        // We can freeze a block if it is 5 blocks or more back from the leading edge.
        long leadingEdgeHeight = leadingEdgeHeight();
        long frozenEdgeHeight = BlockManager.highestBlockFrozen();
        boolean shouldContinue = true;
        while (frozenEdgeHeight < leadingEdgeHeight - 6 && shouldContinue) {
            shouldContinue = false;
            long heightToFreeze = frozenEdgeHeight + 1;
            List<Block> blocksAtFreezeLevel = unfrozenBlocks.get(heightToFreeze);
            if (blocksAtFreezeLevel.size() == 1) {
                Block block = blocksAtFreezeLevel.get(0);
                if (block.getDiscontinuityState() == Block.DiscontinuityState.IsNotDiscontinuity) {
                    BlockManager.freezeBlock(block);
                    TransactionPool.removeTransactionsToHeight(block.getBlockHeight());

                    // Remove all unfrozen blocks at or below the new frozen level.
                    for (Long height : new HashSet<>(unfrozenBlocks.keySet())) {
                        if (height <= heightToFreeze) {
                            unfrozenBlocks.remove(height);
                        }
                    }

                    // Indicate that we should continue trying to freeze blocks.
                    shouldContinue = true;
                    frozenEdgeHeight = heightToFreeze;
                }
            }
        }
    }

    public static synchronized long leadingEdgeHeight() {

        // The leading edge is defined as the greatest block height open for processing at which a valid block without
        // a discontinuity exists.

        long leadingEdgeHeight = -1;
        for (Long height : unfrozenBlocks.keySet()) {
            if (height > leadingEdgeHeight) {
                List<Block> blocksForHeight = unfrozenBlocks.get(height);
                for (int i = 0; i < blocksForHeight.size() && leadingEdgeHeight < height; i++) {
                    Block block = blocksForHeight.get(i);
                    if (block.getDiscontinuityState() == Block.DiscontinuityState.IsNotDiscontinuity) {
                        leadingEdgeHeight = height;
                    }
                }
            }
        }

        // The leading edge cannot be past the open edge.
        leadingEdgeHeight = Math.min(leadingEdgeHeight, BlockManager.openEdgeHeight());

        return leadingEdgeHeight;
    }

    // TODO: make this an instance method of the block class
    public static synchronized CycleInformation cycleInformationForBlock(Block block) {

        ByteBuffer blockVerifier = ByteBuffer.wrap(block.getVerifierIdentifier());
        ByteBuffer localVerifier = ByteBuffer.wrap(Verifier.getIdentifier());

        // Step backward through the chain until we find the beginning of the cycle.
        CycleInformation cycleInformation = null;
        Set<ByteBuffer> identifiers = new HashSet<>();
        long blockVerifierPreviousBlockHeight = -1;
        long localVerifierPreviousBlockHeight = -1;
        Block blockToCheck = block.getPreviousBlock();
        while (blockToCheck != null && cycleInformation == null) {

            ByteBuffer identifier = ByteBuffer.wrap(blockToCheck.getVerifierIdentifier());
            if (identifiers.contains(identifier)) {
                int cycleLength = (int) (block.getBlockHeight() - blockToCheck.getBlockHeight() - 1L);
                int blockVerifierIndexInCycle = blockVerifierPreviousBlockHeight < 0 ? -1 :
                        (int) (blockVerifierPreviousBlockHeight - blockToCheck.getBlockHeight() - 1L);
                int localVerifierIndexInCycle = localVerifierPreviousBlockHeight < 0 ? -1 :
                        (int) (localVerifierPreviousBlockHeight - blockToCheck.getBlockHeight() - 1L);
                cycleInformation = new CycleInformation(cycleLength, blockVerifierIndexInCycle,
                        localVerifierIndexInCycle);
            } else if (blockToCheck.getBlockHeight() == 0) {

                // For purposes of calculation, new verifiers in the first cycle of the chain are treated as existing
                // verifiers.
                int cycleLength = (int) block.getBlockHeight();
                int blockVerifierIndexInCycle = (int) Math.max(blockVerifierPreviousBlockHeight, 0);
                int localVerifierIndexInCycle = (int) Math.max(localVerifierPreviousBlockHeight, 0);

                cycleInformation = new CycleInformation(cycleLength, blockVerifierIndexInCycle,
                        localVerifierIndexInCycle);
            } else {
                identifiers.add(identifier);
            }

            if (identifier.equals(blockVerifier)) {
                blockVerifierPreviousBlockHeight = blockToCheck.getBlockHeight();
            } else if (identifier.equals(localVerifier)) {
                localVerifierPreviousBlockHeight = blockToCheck.getBlockHeight();
            }

            blockToCheck = blockToCheck.getPreviousBlock();
        }

        if (block.getBlockHeight() == 0) {
            cycleInformation = new CycleInformation(0, 0, 0);
        }

        return cycleInformation;
    }

    public static synchronized Block blockToExtendForHeight(long blockHeight) {

        Block blockToExtend = null;
        long highestBlockFrozen = BlockManager.highestBlockFrozen();
        if (blockHeight <= highestBlockFrozen) {
            blockToExtend = BlockManager.frozenBlockForHeight(blockHeight);
        } else {
            List<Block> blocks = unfrozenBlocks.get(blockHeight);
            if (blocks != null) {
                for (Block block : blocks) {
                    if (blockToExtend == null ||
                            block.chainScore(highestBlockFrozen) < blockToExtend.chainScore(highestBlockFrozen)) {
                        blockToExtend = block;
                    } else if (block.chainScore(highestBlockFrozen) == blockToExtend.chainScore(highestBlockFrozen)) {

                        // This can only happen in the case of a new verifier.
                        System.out.println("chain score is equal for two blocks");
                    }
                }
            }
        }

        return blockToExtend;
    }

    public static synchronized Set<Long> unfrozenBlockHeights() {

        return new HashSet<>(unfrozenBlocks.keySet());
    }

    public static int numberOfBlocksAtHeight(long height) {

        int number = 0;
        List<Block> blocks = unfrozenBlocks.get(height);
        if (blocks != null) {
            number = blocks.size();
        }

        return number;
    }

    public static synchronized List<Block> allUnfrozenBlocks() {

        List<Block> allBlocks = new ArrayList<>();
        for (List<Block> blocks : unfrozenBlocks.values()) {
            allBlocks.addAll(blocks);
        }

        return allBlocks;
    }

    public static synchronized Block unfrozenBlockAtHeight(long height, byte[] hash) {

        Block block = null;
        List<Block> blocksAtHeight = unfrozenBlocks.get(height);
        if (blocksAtHeight != null) {
            for (Block blockToCheck : blocksAtHeight) {
                if (ByteUtil.arraysAreEqual(blockToCheck.getHash(), hash)) {
                    block = blockToCheck;
                }
            }
        }

        return block;
    }
}
