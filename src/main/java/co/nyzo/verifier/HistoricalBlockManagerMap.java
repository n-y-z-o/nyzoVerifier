package co.nyzo.verifier;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HistoricalBlockManagerMap {

    // This is a simple class that provides older blocks in the blockchain. We want to provide a service to clients
    // who are interested in older blocks than the BlockManagerMap provides, but we do not want to overload the
    // verifier to provide that service.

    private static long lastFileLoadedTimestamp = 0L;

    private static Map<Long, Block> map = new HashMap<>();

    public static Block blockForHeight(long height) {

        Block block = map.get(height);
        if (block == null && height > 0 && height < BlockManager.getFrozenEdgeHeight() &&
                lastFileLoadedTimestamp < System.currentTimeMillis() - 10000L) {

            lastFileLoadedTimestamp = System.currentTimeMillis();
            reloadMapForBlockHeight(height);
            block = map.get(height);
        }

        return block;
    }

    private static void reloadMapForBlockHeight(long height) {

        // Get the list of blocks from the file.
        long fileIndex = height / BlockManager.blocksPerFile;
        long startHeight = fileIndex * BlockManager.blocksPerFile;
        long endHeight = startHeight + BlockManager.blocksPerFile - 1;
        File file = BlockManager.consolidatedFileForBlockHeight(startHeight);
        List<Block> blocks = BlockManager.loadBlocksInFile(file, startHeight, endHeight);

        // If the list is non-empty, build the map.
        if (!blocks.isEmpty()) {
            Map<Long, Block> map = new HashMap<>();
            for (Block block : blocks) {
                map.put(block.getBlockHeight(), block);
            }
            HistoricalBlockManagerMap.map = map;
        }
    }
}
