package co.nyzo.verifier;

import co.nyzo.verifier.util.NotificationUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.io.File;
import java.util.*;

public class BlockFileConsolidator {

    public static void start() {

        new Thread(new Runnable() {
            @Override
            public void run() {

                while (!UpdateUtil.shouldTerminate()) {

                    try {
                        consolidateFiles();
                    } catch (Exception ignored) { }

                    // Sleep for 5 minutes (300 seconds) in 3-second intervals.
                    for (int i = 0; i < 100 && !UpdateUtil.shouldTerminate(); i++) {
                        try {
                            Thread.sleep(3000L);
                        } catch (Exception ignored) { }
                    }
                }
            }
        }, "BlockFileConsolidator").start();
    }

    private static void consolidateFiles() {

        // Get all files in the individual directory.
        File[] individualFiles = BlockManager.individualBlockDirectory.listFiles();

        // Build a map of all files that need to be consolidated.
        Map<Long, List<File>> fileMap = new HashMap<>();
        long currentFileIndex = BlockManager.frozenEdgeHeight() / BlockManager.blocksPerFile;
        if (individualFiles != null) {
            for (File file : individualFiles) {
                long blockHeight = blockHeightForFile(file);
                long fileIndex = blockHeight / BlockManager.blocksPerFile;
                if (fileIndex < currentFileIndex) {
                    List<File> filesForIndex = fileMap.get(fileIndex);
                    if (filesForIndex == null) {
                        filesForIndex = new ArrayList<>();
                        fileMap.put(fileIndex, filesForIndex);
                    }
                    filesForIndex.add(file);
                }
            }
        }

        // Consolidate the files for each file index.
        for (Long fileIndex : fileMap.keySet()) {
            consolidateFiles(fileIndex, fileMap.get(fileIndex));
        }
    }

    private static void consolidateFiles(long fileIndex, List<File> individualFiles) {

        // Get the blocks from the existing consolidated file for this index.
        long startBlockHeight = fileIndex * BlockManager.blocksPerFile;
        List<Block> blocks = BlockManager.loadBlocksInFile(BlockManager.fileForBlockHeight(startBlockHeight),
                startBlockHeight, startBlockHeight + BlockManager.blocksPerFile - 1, false);

        // Add the blocks from the individual files.
        for (File file : individualFiles) {
            blocks.addAll(BlockManager.loadBlocksInFile(file, 0, Long.MAX_VALUE, false));
        }

        // Sort the blocks on block height ascending.
        Collections.sort(blocks, new Comparator<Block>() {
            @Override
            public int compare(Block block1, Block block2) {
                return ((Long) block1.getBlockHeight()).compareTo(block2.getBlockHeight());
            }
        });

        // Dedupe blocks.
        for (int i = blocks.size() - 1; i > 0; i--) {
            if (blocks.get(i).getBlockHeight() == blocks.get(i - 1).getBlockHeight()) {
                blocks.remove(i);
            }
        }

        // Write the combined file.
        BlockManager.writeBlocksToFile(blocks, BlockManager.fileForBlockHeight(startBlockHeight));

        // Delete the individual files.
        for (File file : individualFiles) {
            file.delete();
        }

        NotificationUtil.send("consolidated " + individualFiles.size() + " files to a single file for start height " +
                startBlockHeight + " on " + Verifier.getNickname());
    }

    private static long blockHeightForFile(File file) {

        long height = -1;
        try {
            String filename = file.getName().replace("i_", "").replace(".nyzoblock", "");
            height = Long.parseLong(filename);
        } catch (Exception ignored) { }

        return height;
    }

}
