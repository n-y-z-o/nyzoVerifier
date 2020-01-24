package co.nyzo.verifier;

import co.nyzo.verifier.util.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class HistoricalBlockManager {

    public static final String startManagerKey = "start_historical_block_manager";
    private static final AtomicBoolean alive = new AtomicBoolean(false);

    public static void start() {

        // Start the manager if the preference indicates. Resource usage is not trivial, so the default is false.
        if (PreferencesUtil.getBoolean(startManagerKey, false) && !alive.getAndSet(true)) {

            new Thread(new Runnable() {
                @Override
                public void run() {

                    while (!UpdateUtil.shouldTerminate()) {
                        try {
                            // Sleep for 5 minutes (300 seconds) in 3-second increments. This is a process that provides
                            // access to 1000-block sections of the Nyzo blockchain history, so each unit of work for
                            // this process covers 7000 seconds of the blockchain. Sleeping for five minutes between
                            // iterations keeps resource usage at a reasonable rate while leaving plenty of room for
                            // eventually building and indexing a full history on this node.
                            for (int i = 0; i < 100; i++) {
                                ThreadUtil.sleep(3000);
                            }

                            // Build an offset file.
                            buildOffsetFile();

                        } catch (Exception e) {
                            LogUtil.println("HistoricalBlockManager: exception in outer thread" +
                                    PrintUtil.printException(e));
                        }
                    }

                    alive.set(false);
                }
            }).start();
        } else {
            LogUtil.println("HistoricalBlockManager: not starting");
        }
    }

    private static void buildOffsetFile() {

        // This is a brute-force process for finding which offset file to build. Just before a consolidated file is
        // written by the block-file consolidator, its corresponding offset file is deleted to ensure that stale offset
        // files do not exist. This process checks all consolidated files backward from the frozen edge. When a
        // consolidated file without an offset file is found, the offset file is built.
        long offsetFileHeight = -1L;
        for (long height = BlockManager.getFrozenEdgeHeight(); height >= 0 && offsetFileHeight < 0;
             height -= BlockManager.blocksPerFile) {
            if (BlockManager.consolidatedFileForBlockHeight(height).exists() && !offsetFileForHeight(height).exists()) {
                offsetFileHeight = height;
            }
        }

        if (offsetFileHeight >= 0) {
            // Calculate the offsets.
            File consolidatedFile = BlockManager.consolidatedFileForBlockHeight(offsetFileHeight);
            int[] offsets = blockOffsetsForConsolidatedFile(consolidatedFile);

            // Write the offsets to the file.
            byte[] offsetBytes = new byte[offsets.length * 4];
            ByteBuffer offsetBuffer = ByteBuffer.wrap(offsetBytes);
            for (int offset : offsets) {
                offsetBuffer.putInt(offset);
            }
            try {
                Files.write(Paths.get(offsetFileForHeight(offsetFileHeight).getAbsolutePath()), offsetBytes);
            } catch (Exception ignored) { }
        }
    }

    private static int[] blockOffsetsForConsolidatedFile(File file) {

        // The result contains a start offset and an end offset for each of the 1000 blocks that might be in the file.
        // The block heights are implicit, relative to the start height of the file. The offsets are 32-bit integers.
        int blocksPerFile = (int) BlockManager.blocksPerFile;
        int[] offsets = new int[blocksPerFile * 2];
        for (int i = 0; i < blocksPerFile; i++) {
            offsets[i] = -1;
        }

        // Generate the offsets.
        if (file.exists()) {
            Path path = Paths.get(file.getAbsolutePath());
            try {
                byte[] fileBytes = Files.readAllBytes(path);
                ByteBuffer buffer = ByteBuffer.wrap(fileBytes);
                int numberOfBlocks = buffer.getShort();
                Block previousBlock = null;
                for (int i = 0; i < numberOfBlocks; i++) {
                    // Record the offset before and after reading the block.
                    int blockStartOffset = buffer.position();
                    Block block = Block.fromByteBuffer(buffer, false);
                    int blockEndOffset = buffer.position();

                    // Read and discard the balance list, if present.
                    if (previousBlock == null || (previousBlock.getBlockHeight() != block.getBlockHeight() - 1)) {
                        BalanceList.fromByteBuffer(buffer);
                    }

                    // Store the offsets in the array.
                    int offsetArrayIndex = (int) (block.getBlockHeight() % BlockManager.blocksPerFile);
                    if (offsetArrayIndex >= 0 && offsetArrayIndex < blocksPerFile) {
                        offsets[offsetArrayIndex * 2] = blockStartOffset;
                        offsets[offsetArrayIndex * 2 + 1] = blockEndOffset;
                    }

                    // Store the block for use in the next iteration.
                    previousBlock = block;
                }
            } catch (Exception ignored) { }
        }

        return offsets;
    }

    public static Block blockForHeight(long height) {

        // First, look to individual files that may not have been consolidated yet.
        File file = BlockManager.individualFileForBlockHeight(height);
        Block block = null;
        if (file.exists()) {
            List<Block> blocksInFile = BlockManager.loadBlocksInFile(file, height, height);
            if (blocksInFile.size() > 0 && blocksInFile.get(0).getBlockHeight() == height) {
                block = blocksInFile.get(0);
            }
        }

        // Next, look to indexed consolidated files.
        File offsetFile = offsetFileForHeight(height);
        if (block == null && offsetFile.exists()) {
            try {
                // Read the offsets from the file into a byte array.
                RandomAccessFile offsetFileReader = new RandomAccessFile(offsetFile, "r");
                offsetFileReader.seek((height % BlockManager.blocksPerFile) * 8);
                byte[] offsetBytes = new byte[8];
                offsetFileReader.read(offsetBytes);
                offsetFileReader.close();

                // Get the offsets as integers.
                ByteBuffer offsetBuffer = ByteBuffer.wrap(offsetBytes);
                int startOffset = offsetBuffer.getInt();
                int endOffset = offsetBuffer.getInt();

                // If the block is in the file, read it.
                if (startOffset >= 0 && endOffset >= 0) {
                    // Read the block bytes from the block file.
                    File consolidatedFile = BlockManager.consolidatedFileForBlockHeight(height);
                    RandomAccessFile blockFileReader = new RandomAccessFile(consolidatedFile, "r");
                    blockFileReader.seek(startOffset);
                    byte[] blockBytes = new byte[endOffset - startOffset];
                    blockFileReader.read(blockBytes);
                    blockFileReader.close();

                    // Make the block from the bytes.
                    ByteBuffer blockBuffer = ByteBuffer.wrap(blockBytes);
                    block = Block.fromByteBuffer(blockBuffer);
                }
            } catch (Exception ignored) { }
        }

        return block;
    }

    public static File offsetFileForHeight(long height) {
        File blockFile = BlockManager.consolidatedFileForBlockHeight(height);
        return new File(blockFile.getAbsolutePath() + "_offsets");
    }
}
