package co.nyzo.verifier;

import co.nyzo.verifier.util.PrintUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class BlockManager {

    public static final File blockRootDirectory = new File(Verifier.dataRootDirectory, "blocks");
    private static final AtomicLong highestBlockFrozen = new AtomicLong(-1L);
    private static final long blocksPerFile = 1000L;
    private static final long filesPerDirectory = 1000L;

    static {
        initialize();
    }

    public static long highestBlockFrozen() {
        return highestBlockFrozen.get();
    }

    public static Block frozenBlockForHeight(long blockHeight) {

        // For a block that should be available, the map is checked first, then local files.
        Block block = null;
        if (blockHeight <= highestBlockFrozen.get()) {

            block = BlockManagerMap.blockForHeight(blockHeight);
            if (block == null) {
                loadBlockFromFile(blockHeight);
                block = BlockManagerMap.blockForHeight(blockHeight);

                // We used to have block loading from the network here. This could cause some serious performance issues
                // for verifiers, though, because we might have very large chains that need to be connected in order
                // to establish the veracity of a block.  So, we will instead allow verifiers to be ignorant of certain
                // parts of the chain. When a verifier starts, it will get the recent chain. If a transaction references
                // a hash of a block that a verifier does not know, the verifier should omit that transaction from
                // the block.
            }
        }

        return block;
    }

    public static List<Block> loadBlocksInFile(File file, boolean addBlocksToCache) {

        List<Block> blocks = new ArrayList<>();
        Path path = Paths.get(file.getAbsolutePath());
        try {
            byte[] fileBytes = Files.readAllBytes(path);
            ByteBuffer buffer = ByteBuffer.wrap(fileBytes);
            int numberOfBlocks = buffer.getShort();
            Block previousBlock = null;
            for (int i = 0; i < numberOfBlocks; i++) {
                Block block = Block.fromByteBuffer(buffer);
                if (previousBlock == null || (previousBlock.getBlockHeight() != block.getBlockHeight() - 1)) {
                    block.setBalanceList(BalanceList.fromByteBuffer(buffer));
                } else {
                    block.setBalanceList(Block.balanceListForNextBlock(previousBlock, block.getTransactions(),
                            block.getVerifierIdentifier()));
                }
                blocks.add(block);

                previousBlock = block;
            }
        } catch (Exception ignored) { }

        if (addBlocksToCache) {
            for (Block block : blocks) {
                BlockManagerMap.addBlock(block);
            }
        }

        return blocks;
    }

    public static boolean writeBlocksToFile(List<Block> blocks, File file) {

        boolean successful = false;

        // Sort the blocks on block height ascending.
        Collections.sort(blocks, new Comparator<Block>() {
            @Override
            public int compare(Block block1, Block block2) {
                return ((Long) block1.getBlockHeight()).compareTo(block2.getBlockHeight());
            }
        });

        int size = 2;  // number of blocks
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            size += block.getByteSize();

            // For the first block and all blocks with gaps, include the balance list.
            if (i == 0 || (blocks.get(i - 1).getBlockHeight() != (blocks.get(i).getBlockHeight() - 1))) {
                size += block.getBalanceList().getByteSize();
            }
        }

        byte[] bytes = new byte[size];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.putShort((short) blocks.size());  // number of blocks
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            buffer.put(block.getBytes());
            if (i == 0 || (blocks.get(i - 1).getBlockHeight() != (blocks.get(i).getBlockHeight() - 1))) {
                buffer.put(block.getBalanceList().getBytes());
            }
        }

        try {
            file.getParentFile().mkdirs();
            file.delete();
            Files.write(Paths.get(file.getAbsolutePath()), bytes);
            successful = true;
        } catch (Exception reportOnly) {
            System.err.println(PrintUtil.printException(reportOnly));
        }

        return successful;
    }

    public static void freezeBlock(Block block) {

        Block previousBlock = BlockManager.frozenBlockForHeight(block.getBlockHeight() - 1);
        if (previousBlock != null) {
            freezeBlock(block, previousBlock.getHash());
        }
    }

    public static synchronized void freezeBlock(Block block, byte[] previousBlockHash) {

        // Only continue if the block's previous hash is correct and the block has not yet been frozen.
        if (ByteUtil.arraysAreEqual(previousBlockHash, block.getPreviousBlockHash()) &&
                block.getBlockHeight() > highestBlockFrozen()) {
            // If the balance list is null, try to create it now.
            if (block.getBalanceList() == null) {
                Block previousBlock = null;
                if (block.getBlockHeight() > 0L) {
                    previousBlock = BlockManager.frozenBlockForHeight(block.getBlockHeight() - 1);
                }
                if (previousBlock != null || block.getBlockHeight() == 0L) {
                    block.setBalanceList(Block.balanceListForNextBlock(previousBlock, block.getTransactions(),
                            block.getVerifierIdentifier()));
                }
            }

            BalanceList balanceList = block.getBalanceList();
            if (balanceList == null) {
                System.err.println("unable to freeze block " + block.getBalanceList() + " because its balance list " +
                        "is null");
            } else {
                try {
                    setHighestBlockFrozen(block.getBlockHeight());

                    File file = fileForBlockHeight(block.getBlockHeight());
                    List<Block> blocksInFile = loadBlocksInFile(file, true);
                    blocksInFile.add(block);
                    writeBlocksToFile(blocksInFile, file);
                    BlockManagerMap.addBlock(block);

                } catch (Exception reportOnly) {
                    reportOnly.printStackTrace();
                    System.err.println("exception writing block to file " + reportOnly.getMessage());
                }
            }
        }
    }

    public static File fileForBlockHeight(long blockHeight, String extension) {

        // This format provides 158.5 years of blocks with nicely aligned names. After that, it will still work fine,
        // but the filenames will be wider.
        long fileIndex = blockHeight / blocksPerFile;
        long directoryIndex = blockHeight / blocksPerFile / filesPerDirectory;
        File directory = new File(blockRootDirectory, String.format("%03d", directoryIndex));
        return new File(directory, String.format("%06d.%s", fileIndex, extension));
    }

    private static File fileForBlockHeight(long blockHeight) {

        return fileForBlockHeight(blockHeight, "nyzoblock");
    }

    private static void loadBlockFromFile(long blockHeight) {

        loadBlocksInFile(fileForBlockHeight(blockHeight), true);
    }

    private static void initialize() {

        // This method only needs to load the locally stored blocks, and it can do so synchronously.

        if (fileForBlockHeight(0).exists()) {

            System.out.println("Genesis block file exists");

            // Load the Genesis block start timestamp.
            List<Block> blocksInGenesisFile = loadBlocksInFile(fileForBlockHeight(0L), true);
            if (blocksInGenesisFile.size() > 0) {
                Block genesisBlock = blocksInGenesisFile.get(0);
                Block.genesisBlockStartTimestamp = genesisBlock.getStartTimestamp();
            }

            // Load the highest block available.
            long highestFileStartBlock = 0L;
            while (fileForBlockHeight(highestFileStartBlock + BlockManager.blocksPerFile).exists()) {
                highestFileStartBlock += BlockManager.blocksPerFile;
            }

            List<Block> blocks = loadBlocksInFile(fileForBlockHeight(highestFileStartBlock), true);
            if (blocks.size() > 0) {
                setHighestBlockFrozen(blocks.get(blocks.size() - 1).getBlockHeight());
            }
        }
    }

    public static void setHighestBlockFrozen(long height) {

        // Freezing a block under the frozen edge is allowed.
        if (height < highestBlockFrozen.get()) {
            System.err.println("Setting highest block frozen to a lesser value than is currently set.");
        } else {
            highestBlockFrozen.set(height);
        }
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

        // A block is considered open for processing 2 seconds after it completes, which is 7 seconds after it starts.
        return Block.genesisBlockStartTimestamp > 0 ?
                ((System.currentTimeMillis() - 7000L - Block.genesisBlockStartTimestamp) / Block.blockDuration) : -1;
    }

    public static void reset() {

        highestBlockFrozen.set(-1L);
    }

    public static boolean readyToProcess() {

        // TODO: wait until we have determined the highest block available
        return highestBlockFrozen() >= 0;
    }
}
