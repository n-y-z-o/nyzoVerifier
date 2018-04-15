package co.nyzo.verifier;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChainOptionManager {

    // All chain options start with the highest frozen block.

    private static List<ChainOption> options = new ArrayList<>();

    private static List<Block> orphanBlocks = new ArrayList<>();

    private static synchronized void loadOptions() {

        long highestBlockFrozen = BlockManager.highestBlockFrozen();
        if (highestBlockFrozen >= 0) {
            Block block = BlockManager.frozenBlockForHeight(highestBlockFrozen);
            options = new ArrayList<>(Arrays.asList(new ChainOption(Arrays.asList(block))));
        }
    }

    public static synchronized List<ChainOption> currentOptions() {

        if (options.isEmpty()) {
            loadOptions();
        }

        return new ArrayList<>(options);
    }

    public static synchronized boolean registerBlock(Block block) {

        boolean shouldForwardBlock = false;
        AtomicBoolean blockIsOrphan = new AtomicBoolean(false);
        AtomicBoolean blockIsDuplicate = new AtomicBoolean(false);
        AtomicBoolean blockIsLowerQuality = new AtomicBoolean(false);

        if (options.isEmpty()) {
            loadOptions();
        }

        CycleInformation cycleInformation = block.getCycleInformation();
        if (block.getBlockHeight() > BlockManager.highestBlockFrozen() && cycleInformation != null) {

            boolean wouldCauseDiscontinuity = false;
            if (cycleInformation.isNewVerifier()) {
                wouldCauseDiscontinuity = block.getBlockHeight() < BlockManager.nextNewVerifierMinimumHeight();
            } else {
                // Rule 3: A block's cycle length must be more than half of the greater of its verifier's previous
                // two cycles (or previous cycle, if the verifier only has one previous cycle). Any block with a
                // shorter cycle is considered a discontinuity in the chain.

            }

            ChainOption matchingChain = matchingChain(block, blockIsOrphan, blockIsDuplicate, blockIsLowerQuality);
            if (matchingChain == null) {

                if (blockIsOrphan.get()) {
                    orphanBlocks.add(block);
                    shouldForwardBlock = true;
                }

            } else {

                matchingChain.appendBlock(block);
                System.out.println("the matching chain now has " + matchingChain.getNumberOfBlocks() + " blocks");
                shouldForwardBlock = true;

                freezeBlockIfPossible();
            }
        }

        return shouldForwardBlock;
    }

    private static synchronized void freezeBlockIfPossible() {


    }

    private static synchronized void freezeBlock(Block block) {

        // These methods are managed and synchronized so that the options should always start one block beyond the
        // highest frozen block. The block manager is in charge of all blocks that are frozen, and the chain option
        // manager is in charge of all blocks that are not yet frozen.
        if (block.getBlockHeight() == BlockManager.highestBlockFrozen() + 1) {
            BlockManager.freezeBlock(block);
            ChainOptionManager.freezeAtBlock(block);
        } else {
            System.err.println("inconsistent state: trying to freezer block at height " + block.getBlockHeight() +
                    " when the highest block frozen is " + BlockManager.highestBlockFrozen());
        }
    }

    private static synchronized ChainOption matchingChain(Block block, AtomicBoolean blockIsOrphan,
                                                          AtomicBoolean blockIsDuplicate,
                                                          AtomicBoolean blockIsLowerQuality) {

        // Registering a block can have the following outcomes:
        // (1) discarding the block because it is unsuitable or inferior to existing options
        // (2) adding the block to an existing chain option
        // (3) replacing a block in an existing chain option
        // (4) adding the block to the new chain option

        if (options.isEmpty()) {
            loadOptions();
        }

        ChainOption matchingChain = null;
        for (ChainOption option : options) {
            Block optionLastBlock = option.getHighestBlock();
            if (optionLastBlock.getBlockHeight() == block.getBlockHeight() - 1 &&
                    ByteUtil.arraysAreEqual(optionLastBlock.getHash(), block.getPreviousBlockHash())) {
                matchingChain = option;

                blockIsOrphan.set(false);
                blockIsDuplicate.set(false);
                blockIsLowerQuality.set(false);
            }
        }

        // TODO: handle replacement, duplicate, and orphan cases
        if (matchingChain == null) {
            blockIsDuplicate.set(true);
            blockIsOrphan.set(false);
            blockIsLowerQuality.set(false);
        }

        return matchingChain;
    }

    public static synchronized void freezeAtBlock(Block blockToFreeze) {

        // This should remove one block at the beginning of each option. Then, any options not built off the frozen
        // block are discarded.
        for (int i = options.size() - 1; i >= 0; i--) {
            ChainOption option = options.get(i);
            Block lowestBlock = option.getLowestBlock();
            while (lowestBlock != null && lowestBlock.getBlockHeight() < blockToFreeze.getBlockHeight()) {
                option.removeLowestBlock();
                lowestBlock = option.getLowestBlock();
            }

            if (lowestBlock == null || !ByteUtil.arraysAreEqual(lowestBlock.getHash(), blockToFreeze.getHash())) {
                System.out.println("removing option at index " + i);
                options.remove(i);
            }
        }
    }

    private static synchronized Block blockForHeightWithHash(long blockHeight, byte[] hash) {

        if (options.isEmpty()) {
            loadOptions();
        }

        Block block = null;
        if (blockHeight <= BlockManager.highestBlockFrozen()) {
            block = BlockManager.frozenBlockForHeight(blockHeight);
        } else {
            for (ChainOption option : options) {
                Block blockToCheck = option.blockAtHeight(blockHeight);
                if (ByteUtil.arraysAreEqual(hash, blockToCheck.getHash())) {
                    block = blockToCheck;
                }
            }
        }

        return block;
    }

    public static synchronized CycleInformation cycleInformationForBlock(Block block) {

        // We need to know:
        // (1) the cycle length
        // (2) whether the verifier of this block is in this cycle and, if so, where it is in the cycle

        ByteBuffer newBlockVerifier = ByteBuffer.wrap(block.getVerifierIdentifier());

        // Step backward through the chain until we find the beginning of the cycle.
        CycleInformation cycleInformation = null;
        Set<ByteBuffer> identifiers = new HashSet<>();
        boolean unableToProcess = false;
        Block nextBlockInChain = block;
        long verifierPreviousBlockHeight = -1;
        for (long blockHeight = block.getBlockHeight() - 1; blockHeight >= 0 && cycleInformation == null &&
                !unableToProcess; blockHeight--) {

            Block blockToCheck = blockForHeightWithHash(blockHeight, nextBlockInChain.getPreviousBlockHash());
            if (blockToCheck == null) {
                unableToProcess = true;
            } else {
                ByteBuffer identifier = ByteBuffer.wrap(blockToCheck.getVerifierIdentifier());
                if (identifiers.contains(identifier)) {
                    int cycleLength = (int) (block.getBlockHeight() - blockHeight - 1L);
                    int verifierIndexInCycle = verifierPreviousBlockHeight < 0 ? -1 :
                            (int) (verifierPreviousBlockHeight - blockHeight - 1L);
                    cycleInformation = new CycleInformation(cycleLength, verifierIndexInCycle);
                } else {
                    identifiers.add(identifier);
                }

                if (identifier.equals(newBlockVerifier)) {
                    verifierPreviousBlockHeight = blockHeight;
                }
            }

            nextBlockInChain = blockToCheck;
        }

        return cycleInformation;
    }

}
