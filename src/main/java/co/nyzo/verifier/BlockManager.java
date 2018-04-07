package co.nyzo.verifier;

import co.nyzo.verifier.util.UpdateUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class BlockManager {

    // TODO: In system initialization, get the latest block files from one other node, independently verify that part
    // TODO: of the chain, then confirm the latest block hash with more than 50% of the network. If we can connect
    // TODO: an already verified part of the chain back to a part of the chain we already know, do that instead.
    // TODO: If we can easily go all the way back to the Genesis block, that's even better.   :)

    public static final File blockRootDirectory = new File(Verifier.dataRootDirectory, "blocks");
    private static final AtomicLong highestBlockFrozen = new AtomicLong(-1L);
    private static final long blocksPerFile = 1000L;
    private static final long filesPerDirectory = 1000L;

    static {
        determineHighestBlockFrozen();
    }

    public static long highestBlockFrozen() {
        return highestBlockFrozen.get();
    }

    public static Block frozenBlockForHeight(long blockHeight) {

        // For a block that should be available, the map is checked first, then local files, then the network.
        Block block = null;
        if (blockHeight <= highestBlockFrozen.get()) {

            block = BlockManagerMap.blockForHeight(blockHeight);
            if (block == null) {
                loadBlockFromFile(blockHeight);
                block = BlockManagerMap.blockForHeight(blockHeight);

                if (block == null) {
                    loadBlockFromNetwork(blockHeight);

                    // Wait up to one second for the block to be fetched from the network.
                    for (int i = 0; i < 10 && block == null; i++) {
                        try {
                            Thread.sleep(100L);
                        } catch (Exception ignored) { }
                        block = BlockManagerMap.blockForHeight(blockHeight);
                    }
                }
            }
        }

        return block;
    }

    private static List<Block> blocksInFile(File file, boolean addBlocksToCache) {

        List<Block> blocks = new ArrayList<>();
        Path path = Paths.get(file.getAbsolutePath());
        try {
            byte[] fileBytes = Files.readAllBytes(path);
            ByteBuffer buffer = ByteBuffer.wrap(fileBytes);
            int numberOfBlocks = buffer.getShort();
            for (int i = 0; i < numberOfBlocks; i++) {
                blocks.add(Block.fromByteBuffer(buffer));
            }
        } catch (Exception reportOnly) {
            System.err.println("exception reading block file: " + reportOnly.getMessage());
        }

        if (addBlocksToCache) {
            for (Block block : blocks) {
                BlockManagerMap.addBlock(block);
            }
        }

        return blocks;
    }

    private static boolean writeBlocksToFile(List<Block> blocks, File file) {

        boolean successful = false;

        int size = 2;  // number of blocks
        for (Block block : blocks) {
            size += block.getByteSize();
        }

        byte[] bytes = new byte[size];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.putShort((short) blocks.size());
        for (Block block : blocks) {
            buffer.put(block.getBytes());
        }

        try {
            file.mkdirs();
            file.delete();
            Files.write(Paths.get(file.getAbsolutePath()), bytes);
            successful = true;
        } catch (Exception reportOnly) {
            System.err.println("error writing blocks to file: " + reportOnly.getMessage());
        }

        return successful;
    }

    public static synchronized void freezeBlock(Block block) {

        if (block.getBlockHeight() == highestBlockFrozen() + 1L) {
            try {
                // Write the balance list to file first so we will have a balance list in a file for each block in a
                // file.
                if (block.getBalanceList().writeToFile()) {
                    File file = fileForBlockHeight(block.getBlockHeight());
                    List<Block> blocksInFile = blocksInFile(file, true);
                    int expectedNumberOfBlocksInFile = (int) (block.getBlockHeight() % blocksPerFile);
                    if (blocksInFile.size() == expectedNumberOfBlocksInFile) {
                        blocksInFile.add(block);
                        writeBlocksToFile(blocksInFile, file);
                        BlockManagerMap.addBlock(block);
                        setHighestBlockFrozen(block.getBlockHeight());
                    } else {
                        System.err.println("unable to write block " + block.getBlockHeight());
                    }

                } else {
                    System.err.println("unsuccessful writing balance list for block " + block.getBlockHeight());
                }
            } catch (Exception reportOnly) {
                reportOnly.printStackTrace();
                System.err.println("exception writing block to file " + reportOnly.getMessage());
            }
        } else {
            System.err.println("the highest block frozen is " + highestBlockFrozen() + "; cannot write block " +
                block.getBlockHeight());
        }
    }

    public static File fileForBlockHeight(long blockHeight, String extension) {

        // This format provides 158.5 years of blocks with nicely aligned names. After that, it will still work fine,
        // but the filenames will be wider.
        long fileIndex = blockHeight / blocksPerFile;
        long directoryIndex = blockHeight / blocksPerFile / filesPerDirectory;
        File directory = new File(blockRootDirectory, String.format("%03d", directoryIndex));
        File file = new File(directory, String.format("%06d.%s", fileIndex, extension));
        System.out.println("file is " + file.getAbsolutePath());

        return file;
    }

    private static File fileForBlockHeight(long blockHeight) {

        return fileForBlockHeight(blockHeight, "nyzoblock");
    }

    private static void loadBlockFromFile(long blockHeight) {

        List<Block> blocks = blocksInFile(fileForBlockHeight(blockHeight), true);
        System.out.println("loaded " + blocks.size() + " blocks for file " + fileForBlockHeight(blockHeight).getName());
    }

    private static void loadBlockFromNetwork(long blockHeight) {

        // TODO
    }

    private static void determineHighestBlockFrozen() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                // Check the local filesystem first. If we have any locally stored blocks, we load them first.
                boolean hasLocalChain = false;
                if (fileForBlockHeight(0).exists()) {
                    long highestFileStartBlock = 0L;
                    while (fileForBlockHeight(highestFileStartBlock + BlockManager.blocksPerFile).exists()) {
                        highestFileStartBlock += BlockManager.blocksPerFile;
                    }

                    System.out.println("highest file start block: " + highestFileStartBlock);
                    List<Block> blocks = blocksInFile(fileForBlockHeight(highestFileStartBlock), true);
                    if (blocks.size() > 0) {
                        setHighestBlockFrozen(blocks.get(blocks.size() - 1).getBlockHeight());
                        hasLocalChain = true;
                    }
                }

                // Wait until we are connected to the mesh, then get the highest frozen block from the mesh.
                while (!NodeManager.connectedToMesh() && !UpdateUtil.shouldTerminate()) {
                    try {
                        Thread.sleep(1000L);
                    } catch (Exception ignored) { }
                }

                if (NodeManager.connectedToMesh()) {
                    // Query up to five nodes to get the highest frozen block from the mesh.
                    long highestBlockAccordingToMesh = 0L;
                    List<Node> nodes = NodeManager.getNodePool();
                    for (int i = 0; i < 5 && !nodes.isEmpty(); i++) {
                        //highestBlockAccordingToMesh = Math.max(highestBlockAccordingToMesh, )
                    }
                }
            }
        }).start();
    }

    public static void setHighestBlockFrozen(long height) {

        if (height < highestBlockFrozen.get()) {
            System.err.println("Setting highest block frozen to a lesser value than is currently set.");
        }

        highestBlockFrozen.set(height);
    }

    public static long heightForTimestamp(long timestamp) {

        return (timestamp - Block.genesisBlockStartTimestamp) / Block.blockDuration;
    }

    public static long startTimestampForHeight(long blockHeight) {

        return Block.genesisBlockStartTimestamp + blockHeight * Block.blockDuration;
    }

    public static long endTimestampForHeight(long blockHeight) {

        return Block.genesisBlockStartTimestamp + (blockHeight + 1L) * Block.blockDuration;
    }

    public static long highestBlockOpenForProcessing() {

        // A block is considered open for processing 3 seconds after it completes, which is 8 seconds after it starts.
        return Block.genesisBlockStartTimestamp > 0 ?
                ((System.currentTimeMillis() - 8000L - Block.genesisBlockStartTimestamp) / Block.blockDuration) : -1;
    }

    public static void reset() {

        highestBlockFrozen.set(-1L);
    }
}
