package co.nyzo.verifier;

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

        // TODO: make separate rules for when we are preparing to process and when we are ready to process

        long leadingEdgeHeight = leadingEdgeHeight();
        long highestBlockFrozen = BlockManager.highestBlockFrozen();

        // A small extra protection to ensure we do not do anything with a block with an invalid signature.
        if (!block.signatureIsValid()) {
            block = null;
        }

        // If the previous block is null, try to set it using information we have available.
        if (block != null && block.getPreviousBlock() == null) {
            long previousBlockHeight = block.getBlockHeight() - 1;
            Block previousBlock = null;
            if (previousBlockHeight <= highestBlockFrozen) {
                previousBlock = BlockManager.frozenBlockForHeight(previousBlockHeight);
            } else {
                List<Block> blocksAtPreviousHeight = unfrozenBlocks.get(previousBlockHeight);
                if (blocksAtPreviousHeight != null) {
                    for (Block blockAtPreviousHeight : blocksAtPreviousHeight) {
                        if (ByteUtil.arraysAreEqual(blockAtPreviousHeight.getHash(), block.getPreviousBlockHash())) {
                            previousBlock = blockAtPreviousHeight;
                        }
                    }
                }
            }

            block.setPreviousBlock(previousBlock);
        }

        CycleInformation cycleInformation = block == null ? null : block.getCycleInformation();
        if (block != null && block.getBlockHeight() > highestBlockFrozen && cycleInformation != null) {

            // Get the list of the block at this height.
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
                if (blocksAtHeight.size() < 100) {
                    blocksAtHeight.add(block);
                    System.out.println("added block at height " + block.getBlockHeight() + " with signature " +
                            ByteUtil.arrayAsStringWithDashes(block.getVerifierSignature()));
                    shouldForwardBlock = true;
                } else {
                    Collections.sort(blocksAtHeight, new Comparator<Block>() {
                        @Override
                        public int compare(Block block1, Block block2) {
                            return ((Long) block2.chainScore(highestBlockFrozen))
                                    .compareTo(block1.chainScore(highestBlockFrozen));
                        }
                    });
                    // TODO: complete this to only keep the top 100 lowest-scoring blocks
                }
            }


        }

        return shouldForwardBlock;
    }

    public static synchronized void freezeBlockIfPossible() {

        // We can freeze a block if it is 5 blocks or more back from the leading edge.
        long leadingEdgeHeight = leadingEdgeHeight();
        long frozenEdgeHeight = BlockManager.highestBlockFrozen();
        if (frozenEdgeHeight < leadingEdgeHeight - 6) {
            long heightToFreeze = frozenEdgeHeight + 1;
            List<Block> blocksAtFreezeLevel = unfrozenBlocks.get(heightToFreeze);
            if (blocksAtFreezeLevel.size() == 1) {
                Block block = blocksAtFreezeLevel.get(0);
                if (block.getDiscontinuityState() == Block.DiscontinuityState.IsNotDiscontinuity) {
                    BlockManager.freezeBlock(block);

                    // Remove all unfrozen blocks at or below the new frozen level.
                    for (Long height : unfrozenBlocks.keySet()) {
                        if (height <= heightToFreeze) {
                            unfrozenBlocks.remove(height);
                        }
                    }
                }
            }
        }
    }

    public static synchronized long leadingEdgeHeight() {

        // The leading edge is defined as the greatest block height at which a valid block without a discontinuity
        // exists.
        long leadingEdgeHeight = -1;
        for (Long height : unfrozenBlocks.keySet()) {
            if (leadingEdgeHeight < height) {
                List<Block> blocksForHeight = unfrozenBlocks.get(height);
                for (int i = 0; i < blocksForHeight.size() && leadingEdgeHeight < height; i++) {
                    Block block = blocksForHeight.get(i);
                    if (block.getCycleInformation() != null) {

                    }
                }
            }
        }

        return leadingEdgeHeight;
    }

    // TODO: make this an instance method of the block class
    public static synchronized CycleInformation cycleInformationForBlock(Block block) {

        ByteBuffer newBlockVerifier = ByteBuffer.wrap(block.getVerifierIdentifier());

        // Step backward through the chain until we find the beginning of the cycle.
        CycleInformation cycleInformation = null;
        Set<ByteBuffer> identifiers = new HashSet<>();
        long verifierPreviousBlockHeight = -1;
        Block blockToCheck = block.getPreviousBlock();
        while (blockToCheck != null && cycleInformation == null) {

            ByteBuffer identifier = ByteBuffer.wrap(blockToCheck.getVerifierIdentifier());
            if (identifiers.contains(identifier)) {
                int cycleLength = (int) (block.getBlockHeight() - blockToCheck.getBlockHeight() - 1L);
                int verifierIndexInCycle = verifierPreviousBlockHeight < 0 ? -1 :
                        (int) (verifierPreviousBlockHeight - blockToCheck.getBlockHeight() - 1L);
                cycleInformation = new CycleInformation(cycleLength, verifierIndexInCycle);
            } else if (blockToCheck.getBlockHeight() == 0) {

                // For purposes of calculation, new verifiers in the first cycle of the chain are treated as existing
                // verifiers.
                int cycleLength = (int) block.getBlockHeight();
                int verifierIndexInCycle;
                if (verifierPreviousBlockHeight > 0) {
                    verifierIndexInCycle = (int) verifierPreviousBlockHeight;
                } else if (identifier.equals(newBlockVerifier)) {
                    verifierIndexInCycle = 0;
                } else {
                    verifierIndexInCycle = 0;
                }
                cycleInformation = new CycleInformation(cycleLength, verifierIndexInCycle);
            } else {
                identifiers.add(identifier);
            }

            if (identifier.equals(newBlockVerifier)) {
                verifierPreviousBlockHeight = blockToCheck.getBlockHeight();
            }

            blockToCheck = blockToCheck.getPreviousBlock();
        }

        if (block.getBlockHeight() == 0) {
            cycleInformation = new CycleInformation(0, 0);
        }

        return cycleInformation;
    }

    public static Block lowestScoredBlockForHeight(long blockHeight) {

        Block lowestScoredBlock = null;
        long highestBlockFrozen = BlockManager.highestBlockFrozen();
        if (blockHeight <= highestBlockFrozen) {
            lowestScoredBlock = BlockManager.frozenBlockForHeight(blockHeight);
        } else {
            List<Block> blocks = unfrozenBlocks.get(blockHeight);
            if (blocks != null) {
                for (Block block : blocks) {
                    if (lowestScoredBlock == null ||
                            block.chainScore(highestBlockFrozen) < lowestScoredBlock.chainScore(highestBlockFrozen)) {
                        lowestScoredBlock = block;
                    }
                }
            }
        }

        return lowestScoredBlock;
    }

    public static Set<Long> unfrozenBlockHeights() {

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

}
