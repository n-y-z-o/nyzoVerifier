package co.nyzo.verifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChainOptionManager {

    private static List<ChainOption> options = new ArrayList<>();

    private static synchronized void loadOptions() {

        long highestBlockFrozen = BlockManager.highestBlockFrozen();
        if (highestBlockFrozen >= 0) {
            Block block = Block.fromFile(highestBlockFrozen);
            options = new ArrayList<>(Arrays.asList(new ChainOption(Arrays.asList(block))));
        } else if (Verifier.shouldRunAsSeed()) {

        }
    }

    public static synchronized List<ChainOption> currentOptions() {

        if (options.isEmpty()) {
            loadOptions();
        }

        return new ArrayList<>(options);
    }

    public static synchronized void registerBlock(Block block) {

        if (options.isEmpty()) {
            loadOptions();
        }

        // Only proceed if the block is past the last frozen block.
        if (block.getBlockHeight() > BlockManager.highestBlockFrozen()) {

            // Find the chain to which this block belongs.
            ChainOption matchingChain = matchingChain(block);
            if (matchingChain == null) {
                // TODO: If the block is a member of a chain we are not currently tracking, decide whether to start
                // TODO: tracking the chain.

            }

            // Add the block to the chain.
            if (matchingChain != null) {
                matchingChain.appendBlock(block);

                System.out.println("the matching chain now has " + matchingChain.getNumberOfBlocks() + " blocks");
            }

            // TODO: freeze any blocks based on the new state of the chain options
            if (options.size() == 1) {
                ChainOption option = options.get(0);
                if (option.getNumberOfBlocks() > 5) {
                    Block blockToFreeze = option.getFirstUnfrozen();
                    freezeBlock(blockToFreeze);
                }
            }
        } else {
            System.err.println("attempted to register block at height " + block.getBlockHeight() + " when block " +
                BlockManager.highestBlockFrozen() + " is frozen");
        }
    }

    private static synchronized void freezeBlock(Block block) {

        // These methods are managed and synchronized so that the options should always start one block beyond the
        // highest frozen block. The block manager is in charge of all blocks that are frozen, and the chain option
        // manager is in charge of all blocks that are not yet frozen.
        if (block.getBlockHeight() == BlockManager.highestBlockFrozen() + 1) {
            block.writeToFile();
            ChainOptionManager.freezeAtBlock(block);
        } else {
            System.err.println("inconsistent state: trying to freezer block at height " + block.getBlockHeight() +
                    " when the highest block frozen is " + BlockManager.highestBlockFrozen());
        }
    }

    private static synchronized ChainOption matchingChain(Block block) {

        if (options.isEmpty()) {
            loadOptions();
        }

        // First, try to find the chain as an existing chain where we can do a simple append.
        ChainOption matchingChain = null;
        for (ChainOption option : options) {
            Block optionLastBlock = option.getHighestBlock();
            if (optionLastBlock.getBlockHeight() == block.getBlockHeight() - 1 &&
                    ByteUtil.arraysAreEqual(optionLastBlock.getHash(), block.getPreviousBlockHash())) {
                matchingChain = option;
            }
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


}
