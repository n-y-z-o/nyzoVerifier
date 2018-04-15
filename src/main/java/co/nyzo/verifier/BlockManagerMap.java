package co.nyzo.verifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BlockManagerMap {

    // This is a parameter we will likely need to revisit. It is here to prevent memory usage from getting out of
    // control, but we need to keep more blocks in memory to determine verification cycle lengths as our mesh grows.
    private static final long maximumBlocksInMap = 20000;

    private static Map<Long, BlockManagerMap> blockMap = new HashMap<>();

    private long lastUsedTimestamp;
    private Block block;

    private BlockManagerMap(Block block) {
        this.block = block;
        this.lastUsedTimestamp = 0L;  // we add some blocks that are not used because it is convenient; this makes sure
                                      // that they will be removed first if they are not used
    }

    public static synchronized void addBlock(Block block) {

        // Add the block to the map.
        blockMap.put(block.getBlockHeight(), new BlockManagerMap(block));

        // Reduce the size of the map if it is too large.
        if (blockMap.keySet().size() > maximumBlocksInMap) {
            long sumTimestamp = 0L;
            for (BlockManagerMap blockWrapper : blockMap.values()) {
                sumTimestamp += blockWrapper.lastUsedTimestamp;
            }

            long cutoffTimestamp = sumTimestamp / blockMap.size();
            Set<Long> blockHeights = new HashSet<>(blockMap.keySet());
            for (long blockHeight : blockHeights) {
                BlockManagerMap blockWrapper = blockMap.get(blockHeight);
                if (blockWrapper.lastUsedTimestamp < cutoffTimestamp) {
                    blockMap.remove(blockHeight);
                }
            }
        }
    }

    public static Block blockForHeight(long blockHeight) {
        BlockManagerMap blockWrapper = blockMap.get(blockHeight);
        Block block = null;
        if (blockWrapper != null) {
            block = blockWrapper.block;
            blockWrapper.lastUsedTimestamp = System.currentTimeMillis();
        }

        return block;
    }

    public static synchronized void reset() {
        blockMap.clear();
    }
}
