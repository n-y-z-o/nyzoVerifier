package co.nyzo.verifier;

import co.nyzo.verifier.util.NotificationUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BlockManagerMap {

    private static int iteration = 0;
    private static Map<Long, Block> blockMap = new HashMap<>();

    public static synchronized void addBlock(Block block) {

        if (block != null) {
            // Add the block to the map.
            blockMap.put(block.getBlockHeight(), block);

            // Periodically remove old blocks.
            if (iteration++ >= 10) {

                iteration = 0;

                long retentionEdgeHeight = BlockManager.getRetentionEdgeHeight();
                for (Long height : new HashSet<>(blockMap.keySet())) {
                    if (height != 0 && height < retentionEdgeHeight) {
                        blockMap.remove(height);
                    }
                }
            }
        }
    }

    public static Block blockForHeight(long blockHeight) {

        return blockMap.get(blockHeight);
    }

    // TODO: remove this; it is for debugging only
    public static synchronized String mapInformation() {

        long minimumHeightInMap = -1L;
        long maximumHeightInMap = -1L;
        boolean hasGenesisBlock = false;
        for (Block block : blockMap.values()) {
            if (block.getBlockHeight() == 0L) {
                hasGenesisBlock = true;
            } else {
                if (minimumHeightInMap < 0) {
                    minimumHeightInMap = block.getBlockHeight();
                    maximumHeightInMap = block.getBlockHeight();
                } else {
                    minimumHeightInMap = Math.min(minimumHeightInMap, block.getBlockHeight());
                    maximumHeightInMap = Math.max(maximumHeightInMap, block.getBlockHeight());
                }
            }
        }

        return blockMap.size() + ": " + (hasGenesisBlock ? "0," : "") +
                (minimumHeightInMap < 0 ? "" : "[" + minimumHeightInMap + "," + maximumHeightInMap + "]");
    }
}
