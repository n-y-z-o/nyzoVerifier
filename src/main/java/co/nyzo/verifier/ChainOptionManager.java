package co.nyzo.verifier;

import java.nio.ByteBuffer;
import java.util.*;

public class ChainOptionManager {

    private static Map<Long, List<Block>> unfrozenBlocks = new HashMap<>();

    public static synchronized boolean registerBlock(Block block) {

        boolean shouldForwardBlock = false;

        long highestBlockRegistered = highestBlockRegistered();
        long highestBlockFrozen = BlockManager.highestBlockFrozen();

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

            // Keep the top 10 blocks at any height.
            List<Block> blocksAtHeight = unfrozenBlocks.get(block.getBlockHeight());
            if (blocksAtHeight == null) {
                blocksAtHeight = new ArrayList<>();
                unfrozenBlocks.put(block.getBlockHeight(), blocksAtHeight);
            }

            // Prevent two blocks from the same identifier from being stored at any height.
            boolean alreadyContainsBlock = false;
            for (int i = 0; i < blocksAtHeight.size() && !alreadyContainsBlock; i++) {
                if (ByteUtil.arraysAreEqual(blocksAtHeight.get(i).getVerifierIdentifier(),
                        block.getVerifierIdentifier())) {
                    alreadyContainsBlock = true;
                }
            }

            if (!alreadyContainsBlock) {
                if (blocksAtHeight.size() < 10) {
                    blocksAtHeight.add(block);
                    shouldForwardBlock = true;
                } else {
                    Collections.sort(blocksAtHeight, new Comparator<Block>() {
                        @Override
                        public int compare(Block block1, Block block2) {
                            return ((Long) block2.chainScore(highestBlockFrozen))
                                    .compareTo(block1.chainScore(highestBlockFrozen));
                        }
                    });
                    // TODO: complete this to only keep the top 10 lowest-scoring blocks
                }
            }


        }

        return shouldForwardBlock;
    }

    public static synchronized long highestBlockRegistered() {

        long highestBlockRegistered = -1;
        for (Long height : unfrozenBlocks.keySet()) {
            highestBlockRegistered = Math.max(height, highestBlockRegistered);
        }

        return highestBlockRegistered;
    }

    private static synchronized void freezeBlockIfPossible() {

        long highestBlockFrozen = BlockManager.highestBlockFrozen();
        long highestBlockRegistered = highestBlockRegistered();

        // If a block is at least 3 back and is the only block on its level, it can be frozen.
    }

    private static synchronized Block blockAtHeightForOption(long height, Block option) {

        Block block = option;
        while (block != null && block.getBlockHeight() > height) {
            block = block.getPreviousBlock();
        }

        return block;
    }

    private static synchronized Block getLowestUnfrozenBlock(Block option) {

        return blockAtHeightForOption(BlockManager.highestBlockFrozen() + 1, option);
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

    public static int numberOfBlocksAtHeight(long height) {

        int number = 0;
        List<Block> blocks = unfrozenBlocks.get(height);
        if (blocks != null) {
            number = blocks.size();
        }

        return number;
    }

}
