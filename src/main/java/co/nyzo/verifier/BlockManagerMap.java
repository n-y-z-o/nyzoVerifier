package co.nyzo.verifier;

import co.nyzo.verifier.util.NotificationUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BlockManagerMap {

    private static int iteration = 0;

    private static Map<Long, BlockManagerMap> blockMap = new HashMap<>();

    private Block block;

    private BlockManagerMap(Block block) {
        this.block = block;
    }

    public static synchronized void addBlock(Block block) {

        // Add the block to the map.
        blockMap.put(block.getBlockHeight(), new BlockManagerMap(block));

        // Periodically remove old blocks.
        if (iteration++ >= 10) {

            iteration = 0;

            long frozenEdgeHeight = BlockManager.frozenEdgeHeight();
            Block frozenEdge = BlockManager.frozenBlockForHeight(frozenEdgeHeight);
            if (frozenEdge.getCycleInformation() != null) {

                long startHeight = frozenEdgeHeight - 1;
                for (int i = 0; i < 4; i++) {
                    startHeight -= frozenEdge.getCycleInformation().getCycleLength(i);
                }

                for (Long height : new HashSet<>(blockMap.keySet())) {
                    if (height != 0 && height < startHeight) {
                        blockMap.remove(height);
                    }
                }
            }
        }
    }

    public static Block blockForHeight(long blockHeight) {
        BlockManagerMap blockWrapper = blockMap.get(blockHeight);
        Block block = null;
        if (blockWrapper != null) {
            block = blockWrapper.block;
        }

        return block;
    }

    // TODO: remove this; it is for debugging only
    public static long mapSize() {

        return blockMap.size();
    }
}
