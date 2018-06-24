package co.nyzo.verifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class BlockManagerMap {

    private static Map<Long, Block> blocks = new HashMap<>();
    private static long minimumBlockHeight = 0;

    public static synchronized void addBlock(Block block) {

        // Add the block to the map.
        long height = block.getBlockHeight();
        if (height == 0L || height >= minimumBlockHeight) {
            blocks.put(height, block);
        }
    }

    public static synchronized void setCycleStartHeight(long cycleStartHeight) {

        // The map should always have the Genesis block and three times the last cycle of blocks loaded into
        // memory and no more. This will be sufficient for all discontinuity calculations and reasonable transaction
        // validation.

        // f - 3 * (f - c) = f - 3f + 3c = -2f + 3c
        long minimumBlockHeight = -2L * BlockManager.frozenEdgeHeight() + 3L * cycleStartHeight;
        if (minimumBlockHeight > BlockManagerMap.minimumBlockHeight) {

            BlockManagerMap.minimumBlockHeight = minimumBlockHeight;

            for (long height : new HashSet<>(blocks.keySet())) {
                if (height != 0L && height < minimumBlockHeight) {
                    blocks.remove(height);
                }
            }
        }
    }

    public static Block blockForHeight(long blockHeight) {

        return blocks.get(blockHeight);
    }
}
