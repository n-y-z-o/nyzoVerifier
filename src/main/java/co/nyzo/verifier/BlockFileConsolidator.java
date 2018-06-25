package co.nyzo.verifier;

import co.nyzo.verifier.util.FileUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class BlockFileConsolidator {

    public static void start() {

        new Thread(new Runnable() {
            @Override
            public void run() {

                while (!UpdateUtil.shouldTerminate()) {

                    consolidateFiles();

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

        // To prevent unbounded work on this, we will only look at the last five files behind the frozen edge.
        long endHeight = (BlockManager.frozenEdgeHeight() / BlockManager.blocksPerFile) * BlockManager.blocksPerFile;
        long startHeight = Math.max(0, endHeight - BlockManager.blocksPerFile * 5L);
        for (long height = startHeight; height < endHeight; height += BlockManager.blocksPerFile) {

            if (BlockManager.fileForBlockHeight(height).exists()) {

                deleteIndividualFilesForHeight(height);

            } else {

                writeCombinedFileForHeight(height);
            }
        }
    }

    private static void deleteIndividualFilesForHeight(long fileStartHeight) {

        long fileEndHeight = fileStartHeight + BlockManager.blocksPerFile - 1;
        for (long height = fileStartHeight; height <= fileEndHeight; height++) {

            File file = BlockManager.individualFileForBlockHeight(height);
            if (file.exists()) {
                file.delete();
            }
        }
    }

    private static void writeCombinedFileForHeight(long fileStartHeight) {

        long fileEndHeight = fileStartHeight + BlockManager.blocksPerFile - 1;
        boolean fileIsComplete = fileEndHeight < BlockManager.frozenEdgeHeight();

        if (fileIsComplete) {

            List<Block> blocksForFile = new ArrayList<>();
            for (long height = fileStartHeight; height <= fileEndHeight; height++) {
                Block block = BlockManager.frozenBlockForHeight(height);
                if (block != null) {
                    blocksForFile.add(block);
                }
            }

            BlockManager.writeBlocksToFile(blocksForFile, BlockManager.fileForBlockHeight(fileStartHeight));
        }
    }

}
