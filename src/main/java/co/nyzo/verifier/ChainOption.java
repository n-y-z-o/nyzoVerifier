package co.nyzo.verifier;

import java.util.ArrayList;
import java.util.List;

public class ChainOption {

    // TODO: remove this class completely -- a chain option should be tracked as the final block in the option

    // All relevant methods in ChainOptionManager are synchronized on the class, so extra synchronization here is
    // unnecessary.

    private List<Block> blocks;

    public ChainOption(List<Block> blocks) {
        this.blocks = new ArrayList<>(blocks);
    }

    public int getNumberOfBlocks() {
        return this.blocks.size();
    }

    public Block getHighestBlock() {
        return blocks.get(blocks.size() - 1);
    }

    public void appendBlock(Block block) {
        blocks.add(block);
    }

    public Block getFirstUnfrozen() {

        Block block = null;
        if (blocks.size() > 1) {
            block = blocks.get(1);
        }

        return block;
    }

    public Block getLowestBlock() {

        Block block = null;
        if (blocks.size() > 0) {
            block = blocks.get(0);
        }

        return block;
    }

    public void removeLowestBlock() {

        if (blocks.size() > 0) {
            blocks.remove(0);
        }
    }
}
