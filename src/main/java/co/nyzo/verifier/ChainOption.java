package co.nyzo.verifier;

import java.util.ArrayList;
import java.util.List;

public class ChainOption {

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

    public long getScore() {

        // TODO: implement this to return the sum verification cycle length
        return 1L;
    }

    public Block blockAtHeight(long blockHeight) {

        int index = (int) (blockHeight - blocks.get(0).getBlockHeight());
        return index < blocks.size() ? blocks.get(index) : null;
    }
}
