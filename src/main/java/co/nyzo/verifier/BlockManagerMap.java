package co.nyzo.verifier;

import co.nyzo.verifier.util.NotificationUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BlockManagerMap {

    private static final BlockManagerMap singleton = new BlockManagerMap();

    private int iteration = 0;
    private Map<Long, Block> blockMap = new HashMap<>();

    public static BlockManagerMap getSingleton() {
        return singleton;
    }

    public synchronized void addBlock(Block block) {

        // Add the block to the map.
        blockMap.put(block.getBlockHeight(), block);

        // Periodically remove old blocks.
        if (iteration++ >= 10) {

            iteration = 0;

            long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
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

    public Block blockForHeight(long blockHeight) {

        return blockMap.get(blockHeight);
    }

    // TODO: remove this; it is for debugging only
    public long mapSize() {

        return blockMap.size();
    }
}
