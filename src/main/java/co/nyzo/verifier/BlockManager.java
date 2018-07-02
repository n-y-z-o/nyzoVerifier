package co.nyzo.verifier;

import co.nyzo.verifier.util.FileUtil;
import co.nyzo.verifier.util.NotificationUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.webSupport.ServerBlockManagerMap;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class BlockManager {

    public static final File blockRootDirectory = new File(Verifier.dataRootDirectory, "blocks");
    public static final File individualBlockDirectory = new File(blockRootDirectory, "individual");
    private static final AtomicLong frozenEdgeHeight = new AtomicLong(-1L);
    public static final long blocksPerFile = 1000L;
    private static final long filesPerDirectory = 1000L;
    private static boolean inGenesisCycle = false;
    private static final Set<ByteBuffer> verifiersInCurrentCycle = new HashSet<>();
    private static long genesisBlockStartTimestamp = -1L;
    private static int currentCycleLength = 0;
    private static BlockManagerMap blockManagerMap = BlockManagerMap.getSingleton();

    static {
        initialize();
    }

    public static long getFrozenEdgeHeight() {
        return frozenEdgeHeight.get();
    }

    public static Block frozenBlockForHeight(long blockHeight) {
        
        Block block = null;
        if (blockHeight <= frozenEdgeHeight.get()) {

            block = blockManagerMap.blockForHeight(blockHeight);
            if (block == null) {  // TODO: restrict which blocks are loaded on demand
                loadBlockFromFile(blockHeight);
                block = blockManagerMap.blockForHeight(blockHeight);
            }
        }

        return block;
    }

    public static synchronized List<Block> loadBlocksInFile(File file, long minimumHeight, long maximumHeight,
                                                            boolean addBlocksToCache) {

        List<Block> blocks = new ArrayList<>();
        if (file.exists()) {
            Path path = Paths.get(file.getAbsolutePath());
            try {
                byte[] fileBytes = Files.readAllBytes(path);
                ByteBuffer buffer = ByteBuffer.wrap(fileBytes);
                int numberOfBlocks = buffer.getShort();
                Block previousBlock = null;
                for (int i = 0; i < numberOfBlocks && (previousBlock == null ||
                        previousBlock.getBlockHeight() < maximumHeight); i++) {
                    Block block = Block.fromByteBuffer(buffer);
                    if (previousBlock == null || (previousBlock.getBlockHeight() != block.getBlockHeight() - 1)) {
                        block.setBalanceList(BalanceList.fromByteBuffer(buffer));
                    } else {
                        block.setBalanceList(Block.balanceListForNextBlock(previousBlock, block.getTransactions(),
                                block.getVerifierIdentifier()));
                    }

                    if (block.getBlockHeight() >= minimumHeight && block.getBlockHeight() <= maximumHeight) {
                        blocks.add(block);
                    }

                    previousBlock = block;
                }
            } catch (Exception ignored) {
            }

            if (addBlocksToCache) {
                for (Block block : blocks) {
                    blockManagerMap.addBlock(block);
                }
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
            FileUtil.writeFile(Paths.get(file.getAbsolutePath()), bytes);
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

        // Only continue if the block's previous hash is correct and the block is past the frozen edge.
        if (ByteUtil.arraysAreEqual(previousBlockHash, block.getPreviousBlockHash()) &&
                block.getBlockHeight() > getFrozenEdgeHeight()) {

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
                    setFrozenEdgeHeight(block.getBlockHeight());

                    writeBlocksToFile(Arrays.asList(block), individualFileForBlockHeight(block.getBlockHeight()));
                    blockManagerMap.addBlock(block);

                    if (block.getBlockHeight() == 0L) {
                        genesisBlockStartTimestamp = block.getStartTimestamp();
                    }

                    updateVerifiersInCurrentCycle(block);

                } catch (Exception reportOnly) {
                    reportOnly.printStackTrace();
                    System.err.println("exception writing block to file " + reportOnly.getMessage());
                }
            }
        }
    }

    public static File individualFileForBlockHeight(long blockHeight) {

        return new File(individualBlockDirectory, String.format("i_%09d.%s", blockHeight, "nyzoblock"));
    }

    public static File fileForBlockHeight(long blockHeight) {

        // This format provides 158.5 years of blocks with nicely aligned names. After that, it will still work fine,
        // but the filenames will be wider.
        long fileIndex = blockHeight / blocksPerFile;
        long directoryIndex = blockHeight / blocksPerFile / filesPerDirectory;
        File directory = new File(blockRootDirectory, String.format("%03d", directoryIndex));
        return new File(directory, String.format("%06d.%s", fileIndex, "nyzoblock"));
    }

    private static void loadBlockFromFile(long blockHeight) {

        loadBlocksInFile(individualFileForBlockHeight(blockHeight), blockHeight, blockHeight, true);
        if (blockManagerMap.blockForHeight(blockHeight) == null) {
            loadBlocksInFile(fileForBlockHeight(blockHeight), blockHeight, blockHeight, true);
        }
    }

    private static synchronized void initialize() {


        // This method only needs to load the locally stored blocks, and it can do so synchronously.

        // Ensure that both the block directory and the individual block directory exist. The individual block directory
        // is a subdirectory of the block directory, so a single call can ensure both.
        individualBlockDirectory.mkdirs();

        Block genesisBlock = frozenBlockForHeight(0L);
        if (genesisBlock != null) {

            genesisBlockStartTimestamp = genesisBlock.getStartTimestamp();

            // Load the highest block available.
            long highestFileStartBlock = 0L;
            while (fileForBlockHeight(highestFileStartBlock + BlockManager.blocksPerFile).exists()) {
                highestFileStartBlock += BlockManager.blocksPerFile;
            }

            /*
            // Load the highest consolidated file.
            List<Block> blocks = loadBlocksInFile(fileForBlockHeight(highestFileStartBlock), true);
            Block block = null;
            if (blocks.size() > 0) {
                block = blocks.get(blocks.size() - 1);
            }

            // Continue trying to load individual files that have not yet been consolidated.
            if (block != null) {
                long blockHeight = block.getBlockHeight();
                while (individualFileForBlockHeight(blockHeight + 1).exists()) {
                    blockHeight++;
                }

                if (individualFileForBlockHeight(blockHeight).exists()) {
                    List<Block> individualBlockList = loadBlocksInFile(individualFileForBlockHeight(blockHeight), true);
                    if (individualBlockList.size() > 0) {
                        block = individualBlockList.get(0);
                    }
                }

                setFrozenEdgeHeight(block.getBlockHeight());
                updateVerifiersInCurrentCycle(block);
            }*/
        }
    }

    public static void setFrozenEdgeHeight(long height) {

        // Freezing a block under the frozen edge is allowed.
        if (height < frozenEdgeHeight.get()) {
            System.err.println("Setting highest block frozen to a lesser value than is currently set.");
        } else {
            frozenEdgeHeight.set(height);
        }
    }

    public static long getGenesisBlockStartTimestamp() {

        return genesisBlockStartTimestamp;
    }

    public static void setGenesisBlockStartTimestamp(long genesisBlockStartTimestamp) {

        BlockManager.genesisBlockStartTimestamp = genesisBlockStartTimestamp;
    }

    public static long heightForTimestamp(long timestamp) {

        return (timestamp - genesisBlockStartTimestamp) / Block.blockDuration;
    }

    public static long startTimestampForHeight(long blockHeight) {

        return genesisBlockStartTimestamp + blockHeight * Block.blockDuration;
    }

    public static long endTimestampForHeight(long blockHeight) {

        return genesisBlockStartTimestamp + (blockHeight + 1L) * Block.blockDuration;
    }

    public static long openEdgeHeight(boolean forRegistration) {

        // A block is considered open for processing 1.5 seconds after it completes, which is 6.5 seconds after it
        // starts. For registration, we reduce the offset to 0.5 seconds to avoid rejecting blocks due to minor clock
        // differences.
        long offset = forRegistration ? 5500L : 6500L;

        return genesisBlockStartTimestamp > 0 ?
                ((System.currentTimeMillis() - offset - genesisBlockStartTimestamp) / Block.blockDuration) : -1;
    }

    public static boolean inGenesisCycle() {

        return inGenesisCycle;
    }

    public static int currentCycleLength() {

        return currentCycleLength;
    }

    public static synchronized Set<ByteBuffer> verifiersInCurrentCycle() {

        return new HashSet<>(verifiersInCurrentCycle);
    }

    public static synchronized boolean verifierInCurrentCycle(ByteBuffer identifier) {

        return verifiersInCurrentCycle.contains(identifier);
    }

    private static synchronized void updateVerifiersInCurrentCycle(Block block) {

        boolean foundCycle = false;
        verifiersInCurrentCycle.clear();
        while (block != null && !foundCycle) {

            ByteBuffer identifierBuffer = ByteBuffer.wrap(block.getVerifierIdentifier());
            if (verifiersInCurrentCycle.contains(identifierBuffer)) {
                foundCycle = true;
            } else {
                verifiersInCurrentCycle.add(identifierBuffer);
            }

            inGenesisCycle = block.getBlockHeight() == 0 && !foundCycle;
            block = block.getPreviousBlock();
        }

        // Update the cycle value. This is stored separately so the method can be made un-synchronized without question.
        currentCycleLength = verifiersInCurrentCycle.size();
    }
}
