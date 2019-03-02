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

                    // Sleep for 5 minutes (300 seconds) in 3-second intervals.
                    for (int i = 0; i < 100 && !UpdateUtil.shouldTerminate(); i++) {
                        try {
                            Thread.sleep(3000L);
                        } catch (Exception ignored) { }
                    }

                    try {
                        consolidateFiles();
                    } catch (Exception ignored) { }
                }
            }
        }, "BlockFileConsolidator").start();
    }

    private static void consolidateFiles() {

        // Get all files in the individual directory.
        File[] individualFiles = BlockManager.individualBlockDirectory.listFiles();

        // Build a map of all files that need to be consolidated. Before, files were consolidated as soon as the frozen
        // edge passed them. Now, files are consolidated when the retention edge passes them.
        Map<Long, List<File>> fileMap = new HashMap<>();
        long currentFileIndex = BlockManager.getRetentionEdgeHeight() / BlockManager.blocksPerFile;
        if (individualFiles != null) {
            for (File file : individualFiles) {
                long blockHeight = blockHeightForFile(file);
                if (blockHeight > 0) {
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
        }

        // Consolidate the files for each file index.
        for (Long fileIndex : fileMap.keySet()) {
            consolidateFiles(fileIndex, fileMap.get(fileIndex));
        }
    }

    private static void consolidateFiles(long fileIndex, List<File> individualFiles) {

        // Get the blocks from the existing consolidated file for this index.
        long startBlockHeight = fileIndex * BlockManager.blocksPerFile;
        File consolidatedFile = BlockManager.consolidatedFileForBlockHeight(startBlockHeight);
        List<Block> blocks = BlockManager.loadBlocksInFile(consolidatedFile, startBlockHeight, startBlockHeight +
                BlockManager.blocksPerFile - 1);

        // Add the blocks from the individual files.
        for (File file : individualFiles) {
            blocks.addAll(BlockManager.loadBlocksInFile(file, 0, Long.MAX_VALUE));
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

        // Get the balance lists.
        List<BalanceList> balanceLists = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            if (i == 0 || blocks.get(i).getBlockHeight() != (blocks.get(i - 1).getBlockHeight() + 1)) {
                BalanceList balanceList = BlockManager.loadBalanceListFromFileForHeight(blocks.get(i).getBlockHeight());
                if (balanceList == null) {
                    NotificationUtil.send("unexpected null balance list at height " + blocks.get(i).getBlockHeight() +
                            " in block consolidation process on " + Verifier.getNickname());
                } else {
                    balanceLists.add(balanceList);
                }
            }
        }

        // Write the combined file.
        BlockManager.writeBlocksToFile(blocks, balanceLists, consolidatedFile);

        // Delete the individual files. Do not delete the Genesis file, because it will continue to be used in regular
        // operation.
        individualFiles.remove(BlockManager.individualFileForBlockHeight(0L));
        for (File file : individualFiles) {
            file.delete();
        }

        NotificationUtil.send("consolidated " + individualFiles.size() + " files to a single file for start height " +
                startBlockHeight + " on " + Verifier.getNickname() + "; used " + balanceLists.size() +
                " balance lists");
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
