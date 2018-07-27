package co.nyzo.verifier;

import co.nyzo.verifier.util.DebugUtil;
import co.nyzo.verifier.util.FileUtil;
import co.nyzo.verifier.util.NotificationUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.io.File;
import java.io.FileFilter;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class BlockManager {

    public static final File blockRootDirectory = new File(Verifier.dataRootDirectory, "blocks");
    public static final File individualBlockDirectory = new File(blockRootDirectory, "individual");
    private static long trailingEdgeHeight = -1L;
    private static long frozenEdgeHeight = -1L;
    public static final long blocksPerFile = 1000L;
    private static final long filesPerDirectory = 1000L;
    private static boolean inGenesisCycle = false;
    private static final Set<ByteBuffer> verifiersInCurrentCycle = new HashSet<>();
    private static long genesisBlockStartTimestamp = -1L;
    private static int currentCycleLength = 0;
    private static boolean initialized = false;

    static {
        initialize();
    }

    public static long getFrozenEdgeHeight() {

        return frozenEdgeHeight;
    }

    public static long getTrailingEdgeHeight() {

        return trailingEdgeHeight;
    }

    public static long getRetentionEdgeHeight() {

        // To keep the mesh playing smoothly while reasonably limiting resource usage, we retain information in memory
        // to just behind the trailing edge. Twenty-four blocks gives us two minutes of leeway.
        return trailingEdgeHeight - 24;
    }

    public static Block frozenBlockForHeight(long blockHeight) {
        
        Block block = null;
        if (blockHeight <= frozenEdgeHeight) {

            // First, look to the map.
            block = BlockManagerMap.blockForHeight(blockHeight);

            // If initialization has not completed, load the block into the standard map.
            if (block == null && !initialized) {
                block = loadBlockFromFile(blockHeight);
                if (block != null) {
                    BlockManagerMap.addBlock(block);
                }
            }

            // If initialization has completed, the only other option is the historical map.
            if (block == null) {
                block = HistoricalBlockManagerMap.blockForHeight(blockHeight);
            }
        }

        return block;
    }

    public static synchronized List<Block> loadBlocksInFile(File file, long minimumHeight, long maximumHeight) {

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
                        // Read and discard the balance list.
                        BalanceList.fromByteBuffer(buffer);
                    }

                    if (block.getBlockHeight() >= minimumHeight && block.getBlockHeight() <= maximumHeight) {
                        blocks.add(block);
                    }

                    previousBlock = block;
                }
            } catch (Exception ignored) { }
        }

        return blocks;
    }

    public static boolean writeBlocksToFile(List<Block> blocks, List<BalanceList> balanceLists, File file) {

        Map<Long, BalanceList> balanceListMap = new HashMap<>();
        for (BalanceList balanceList : balanceLists) {
            balanceListMap.put(balanceList.getBlockHeight(), balanceList);
        }

        boolean successful = true;

        // Sort the blocks on block height ascending.
        Collections.sort(blocks, new Comparator<Block>() {
            @Override
            public int compare(Block block1, Block block2) {
                return ((Long) block1.getBlockHeight()).compareTo(block2.getBlockHeight());
            }
        });

        int byteSize = 2;  // number of blocks is stored as a short
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            byteSize += block.getByteSize();

            // For the first block and all blocks with gaps, include the balance list.
            if (i == 0 || (blocks.get(i - 1).getBlockHeight() != (block.getBlockHeight() - 1))) {
                // TODO: confirm this works properly with balance list change
                BalanceList balanceList = balanceListMap.get(block.getBlockHeight());
                if (balanceList == null) {
                    successful = false;
                } else {
                    byteSize += balanceList.getByteSize();
                }
            }
        }

        byte[] bytes = new byte[byteSize];
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.putShort((short) blocks.size());  // number of blocks
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            buffer.put(block.getBytes());
            if (i == 0 || (blocks.get(i - 1).getBlockHeight() != (block.getBlockHeight() - 1))) {
                // TODO: confirm this works properly with balance list change
                BalanceList balanceList = balanceListMap.get(block.getBlockHeight());
                if (balanceList == null) {
                    successful = false;
                } else {
                    buffer.put(balanceList.getBytes());
                }
            }
        }

        if (successful) {
            try {
                file.getParentFile().mkdirs();
                FileUtil.writeFile(Paths.get(file.getAbsolutePath()), bytes);
                successful = true;
            } catch (Exception reportOnly) {
                System.err.println(PrintUtil.printException(reportOnly));
            }
        }

        if (!successful) {
            NotificationUtil.send("unable to write block file " + file.getName() + " on " + Verifier.getNickname());
        }

        return successful;
    }

    public static void freezeBlock(Block block) {

        Block previousBlock = frozenBlockForHeight(block.getBlockHeight() - 1);
        if (previousBlock != null) {
            BalanceList balanceList = BalanceListManager.balanceListForBlock(block, null);
            freezeBlock(block, previousBlock.getHash(), balanceList);
        }
    }

    public static synchronized void freezeBlock(Block block, byte[] previousBlockHash, BalanceList balanceList) {

        if (block.getBlockHeight() == 0L) {
            NotificationUtil.send("freezing Genesis block on " + Verifier.getNickname() + ": " +
                    DebugUtil.callingMethods(8));
        }

        // Only continue if the block's previous hash is correct and the block is past the frozen edge.
        if (ByteUtil.arraysAreEqual(previousBlockHash, block.getPreviousBlockHash()) &&
                block.getBlockHeight() > getFrozenEdgeHeight()) {

            if (balanceList == null) {
                NotificationUtil.send("unable to freeze block " + block.getBlockHeight() + " on " +
                        Verifier.getNickname() + " because its balance list is null");
            } else {
                try {
                    setFrozenEdge(block);
                    BalanceListManager.registerBalanceList(balanceList);

                    writeBlocksToFile(Arrays.asList(block), Arrays.asList(balanceList),
                            individualFileForBlockHeight(block.getBlockHeight()));

                    if (block.getBlockHeight() == 0L) {
                        genesisBlockStartTimestamp = block.getStartTimestamp();
                    }

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

    public static File consolidatedFileForBlockHeight(long blockHeight) {

        // This format provides 158.5 years of blocks with nicely aligned names. After that, it will still work fine,
        // but the filenames will be wider, so we should re-check loading at that point.  ;)
        long fileIndex = blockHeight / blocksPerFile;
        long directoryIndex = blockHeight / blocksPerFile / filesPerDirectory;
        File directory = new File(blockRootDirectory, String.format("%03d", directoryIndex));
        return new File(directory, String.format("%06d.%s", fileIndex, "nyzoblock"));
    }

    private static Block loadBlockFromFile(long blockHeight) {

        // Try to first load the block from the individual file. If the block is not there, try to load the block from
        // the consolidated file.
        List<Block> blocks = loadBlocksInFile(individualFileForBlockHeight(blockHeight), blockHeight, blockHeight);
        Block block = null;
        if (!blocks.isEmpty() && blocks.get(0).getBlockHeight() == blockHeight) {
            block = blocks.get(0);
        } else {
            blocks = loadBlocksInFile(consolidatedFileForBlockHeight(blockHeight), blockHeight, blockHeight);
            if (!blocks.isEmpty() && blocks.get(0).getBlockHeight() == blockHeight) {
                block = blocks.get(0);
            }
        }
        return block;
    }

    private static synchronized void initialize() {

        // This method only needs to load the locally stored blocks, and it can do so synchronously.

        // Ensure that both the block directory and the individual block directory exist. The individual block directory
        // is a subdirectory of the block directory, so a single call can ensure both.
        individualBlockDirectory.mkdirs();

        // Try to load the Genesis block from file.
        Block genesisBlock = loadBlockFromFile(0L);
        if (genesisBlock != null) {

            // Set the frozen edge height to the Genesis block level.
            genesisBlockStartTimestamp = genesisBlock.getStartTimestamp();
            setFrozenEdge(genesisBlock);

            // Find the highest consolidated file.
            // TODO: test this with several million blocks to ensure correct and efficient loading
            long highestConsolidatedFileStartHeight = findHighestConsolidatedFileStartHeight();
            long highestConsolidatedFileEndHeight = highestConsolidatedFileStartHeight + blocksPerFile - 1L;

            // Load the highest block in the consolidated file and set it as the frozen edge.
            File consolidatedFile = consolidatedFileForBlockHeight(highestConsolidatedFileStartHeight);
            List<Block> blocks = loadBlocksInFile(consolidatedFile, highestConsolidatedFileStartHeight,
                    highestConsolidatedFileEndHeight);
            if (blocks.size() > 0) {
                Block block = blocks.get(blocks.size() - 1);
                setFrozenEdge(block);
            }

            // Now try to load the highest block that has not yet been consolidated.
            long highestIndividualFileHeight = findHighestIndividualFileHeight();
            if (highestIndividualFileHeight > getFrozenEdgeHeight()) {

                File individualFile = individualFileForBlockHeight(highestIndividualFileHeight);
                List<Block> individualBlockList = loadBlocksInFile(individualFile, highestIndividualFileHeight,
                        highestIndividualFileHeight);
                if (individualBlockList.size() > 0) {
                    Block block = individualBlockList.get(0);
                    setFrozenEdge(block);
                }
            }

            // Load from the trailing edge to the frozen edge. This gives us the blocks necessary to immediately serve
            // bootstrap response requests. Skip this if the trailing edge is invalid, because we might have a gap
            // but many old blocks.
            if (getTrailingEdgeHeight() >= 0) {
                for (long i = getTrailingEdgeHeight(); i <= getFrozenEdgeHeight(); i++) {
                    frozenBlockForHeight(i);
                }
            }

            NotificationUtil.send("initialized frozen edge to " + BlockManager.getFrozenEdgeHeight() + " on " +
                    Verifier.getNickname());

            // Load the balance lists of the trailing and frozen edges into the balance list manager. This gives us the
            // balance lists necessary to immediately serve bootstrap response requests.
            BalanceList trailingEdgeBalanceList = null;
            for (long height = getTrailingEdgeHeight(); height < getFrozenEdgeHeight() &&
                    trailingEdgeBalanceList == null; height++) {
                trailingEdgeBalanceList = loadBalanceListFromFileForHeight(height);
            }
            BalanceList frozenEdgeBalanceList = loadBalanceListFromFileForHeight(getFrozenEdgeHeight());
            BalanceListManager.registerBalanceList(trailingEdgeBalanceList);
            BalanceListManager.registerBalanceList(frozenEdgeBalanceList);

            initialized = true;
        }
    }

    public static BalanceList loadBalanceListFromFileForHeight(long blockHeight) {

        BalanceList balanceList = loadBalanceListFromFile(individualFileForBlockHeight(blockHeight), blockHeight);
        if (balanceList == null) {
            loadBalanceListFromFile(consolidatedFileForBlockHeight(blockHeight), blockHeight);
        }

        return balanceList;
    }

    private static BalanceList loadBalanceListFromFile(File file, long blockHeight) {

        BalanceList blockBalanceList = null;
        if (file.exists()) {
            Path path = Paths.get(file.getAbsolutePath());
            try {
                byte[] fileBytes = Files.readAllBytes(path);
                ByteBuffer buffer = ByteBuffer.wrap(fileBytes);
                int numberOfBlocks = buffer.getShort();
                Block previousBlock = null;
                BalanceList balanceList = null;
                for (int i = 0; i < numberOfBlocks && blockBalanceList == null; i++) {
                    Block block = Block.fromByteBuffer(buffer);
                    if (previousBlock == null || (previousBlock.getBlockHeight() != block.getBlockHeight() - 1)) {
                        balanceList = BalanceList.fromByteBuffer(buffer);
                    } else {
                        balanceList = Block.balanceListForNextBlock(previousBlock, balanceList, block.getTransactions(),
                                block.getVerifierIdentifier());
                    }

                    if (block.getBlockHeight() == blockHeight) {
                        blockBalanceList = balanceList;

                        if (!ByteUtil.arraysAreEqual(blockBalanceList.getHash(), block.getBalanceListHash())) {
                            System.err.println("incorrect hash for balance list");
                            blockBalanceList = null;
                        }
                    }

                    previousBlock = block;
                }
            } catch (Exception ignored) { }
        }

        return blockBalanceList;
    }

    private static long findHighestConsolidatedFileStartHeight() {

        long startHeight = -1L;

        List<File> directories = Arrays.asList(blockRootDirectory.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory();
            }
        }));

        Collections.sort(directories, new Comparator<File>() {
            @Override
            public int compare(File file1, File file2) {
                return file2.getName().compareTo(file1.getName());
            }
        });

        for (int j = 0; j < directories.size() && startHeight < 0; j++) {
            try {
                List<File> files = Arrays.asList(directories.get(j).listFiles());
                Collections.sort(files, new Comparator<File>() {
                    @Override
                    public int compare(File file1, File file2) {
                        return file2.compareTo(file1);
                    }
                });

                for (int i = 0; i < files.size() && startHeight < 0; i++) {
                    try {
                        long fileIndex = Long.parseLong(files.get(i).getName().replace(".nyzoblock", ""));
                        startHeight = fileIndex * blocksPerFile;
                    } catch (Exception ignored) { }
                }

            } catch (Exception ignored) { }
        }

        return startHeight;
    }

    private static long findHighestIndividualFileHeight() {

        long height = -1L;

        try {
            List<File> files = Arrays.asList(individualBlockDirectory.listFiles());

            Collections.sort(files, new Comparator<File>() {
                @Override
                public int compare(File file1, File file2) {
                    return file2.getName().compareTo(file1.getName());
                }
            });

            for (int i = 0; i < files.size() && height < 0; i++) {
                try {
                    height = Long.parseLong(files.get(i).getName().replace("i_", "").replace(".nyzoblock", ""));
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) { }

        return height;
    }

    public static synchronized void setFrozenEdge(Block block) {

        // Freezing a block under the frozen edge is not allowed.
        if (block.getBlockHeight() < frozenEdgeHeight) {
            System.err.println("Setting highest block frozen to a lesser value than is currently set.");
        } else {
            // Set the frozen and trailing edge heights.
            frozenEdgeHeight = block.getBlockHeight();
            if (block.getCycleInformation() != null) {
                trailingEdgeHeight = Math.max(block.getCycleInformation().getWindowStartHeight(), 0);
            }

            updateVerifiersInCurrentCycle(block);
        }

        // Always add the block to the map. This should be done after the frozen edge is set, because the map looks at
        // the frozen edge.
        BlockManagerMap.addBlock(block);
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

    public static synchronized void requestMissingBlocks() {

        // TODO: implement
    }
}
