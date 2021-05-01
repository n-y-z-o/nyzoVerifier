package co.nyzo.verifier;

import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.util.*;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class BlockManager {

    public static final File blockRootDirectory = new File(Verifier.dataRootDirectory, "blocks");
    public static final File individualBlockDirectory = new File(blockRootDirectory, "individual");
    private static long trailingEdgeHeight = -1L;
    private static long frozenEdgeHeight = -1L;
    private static Block frozenEdge = null;
    public static final long blocksPerFile = 1000L;
    private static final long filesPerDirectory = 1000L;
    private static boolean inGenesisCycle = false;
    private static long currentCycleEndHeight = -2L;
    private static List<ByteBuffer> currentCycleList = new ArrayList<>();
    private static Set<ByteBuffer> currentCycleSet = ConcurrentHashMap.newKeySet();
    private static Set<ByteBuffer> currentAndNearCycleSet = ConcurrentHashMap.newKeySet();
    private static Set<Node> currentAndNearCycleNodes = ConcurrentHashMap.newKeySet();
    private static long genesisBlockStartTimestamp = -1L;
    private static final AtomicBoolean startedInitialization = new AtomicBoolean(false);
    private static final AtomicBoolean completedInitialization = new AtomicBoolean(false);
    private static boolean cycleComplete = false;

    private static final String lastVerifierJoinHeightKey = "last_verifier_join_height";
    private static long lastVerifierJoinHeight = PersistentData.getLong(lastVerifierJoinHeightKey, -1L);

    private static final String lastVerifierRemovalHeightKey = "last_verifier_removal_height";
    private static long lastVerifierRemovalHeight = PersistentData.getLong(lastVerifierRemovalHeightKey, -1L);

    public static void main(String[] args) {

        initialize();
        UpdateUtil.terminate();
    }

    public static boolean completedInitialization() {

        return completedInitialization.get();
    }

    public static long getFrozenEdgeHeight() {

        return frozenEdgeHeight;
    }

    public static Block getFrozenEdge() {

        return frozenEdge;
    }

    public static long getTrailingEdgeHeight() {

        if (trailingEdgeHeight < 0) {
            Block frozenEdge = frozenBlockForHeight(getFrozenEdgeHeight());
            if (frozenEdge == null || frozenEdge.getCycleInformation() == null) {
                trailingEdgeHeight = -1L;
            } else {
                trailingEdgeHeight = Math.max(frozenEdge.getCycleInformation().getDeterminationHeight(), 0);
            }
        }

        return trailingEdgeHeight;
    }

    public static long getRetentionEdgeHeight() {

        // To keep the mesh playing smoothly while reasonably limiting resource usage, we retain information in memory
        // to just behind the trailing edge. Twenty-four blocks gives us 2.8 minutes of leeway.
        long trailingEdgeHeight = getTrailingEdgeHeight();
        return trailingEdgeHeight == -1L ? -1L : Math.max(0, trailingEdgeHeight - 24);
    }

    public static boolean likelyAcceptingNewVerifiers() {
        return frozenEdge != null && frozenEdge.getCycleInformation() != null && frozenEdge.getBlockHeight() >
                getLastVerifierJoinHeight() + frozenEdge.getCycleInformation().getCycleLength() * 2;
    }

    public static long getLastVerifierJoinHeight() {
        return lastVerifierJoinHeight;
    }

    public static long getLastVerifierRemovalHeight() {
        return lastVerifierRemovalHeight;
    }

    public static Block frozenBlockForHeight(long blockHeight) {

        Block block = null;
        if (blockHeight <= frozenEdgeHeight) {

            // First, look to the map.
            block = BlockManagerMap.blockForHeight(blockHeight);

            // If initialization has not completed, load the block into the standard map.
            if (block == null && !completedInitialization()) {
                block = loadBlockFromFile(blockHeight);
                if (block != null) {
                    BlockManagerMap.addBlock(block);
                }
            }
        }

        return block;
    }

    private static Block loadBlockFromIndividualFile(long blockHeight) {

        Block block = null;
        File file = individualFileForBlockHeight(blockHeight);
        if (file.exists()) {
            try {
                RandomAccessFile blockFileReader = new RandomAccessFile(file, "r");
                int numberOfBlocks = blockFileReader.readShort();
                block = Block.fromFile(blockFileReader);
                blockFileReader.close();
            } catch (Exception ignored) { }
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
                    Block block = Block.fromByteBuffer(buffer, false);
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

        // Determine the temporary file and ensure the location is available.
        File temporaryFile = new File(file.getAbsolutePath() + "_temp");
        temporaryFile.delete();

        // Attempt to write the file.
        boolean successful = true;
        try {
            // Open the file. The "rw" argument makes the file writable.
            RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");

            // Sort the blocks on block height ascending.
            blocks.sort(new Comparator<Block>() {
                @Override
                public int compare(Block block1, Block block2) {
                    return Long.compare(block1.getBlockHeight(), block2.getBlockHeight());
                }
            });

            // Make a map of the balance lists for easy lookup.
            Map<Long, BalanceList> balanceListMap = new HashMap<>();
            for (BalanceList balanceList : balanceLists) {
                balanceListMap.put(balanceList.getBlockHeight(), balanceList);
            }

            randomAccessFile.writeShort((short) blocks.size());  // number of blocks
            for (int i = 0; i < blocks.size(); i++) {
                Block block = blocks.get(i);
                randomAccessFile.write(block.getBytes());
                if (i == 0 || (blocks.get(i - 1).getBlockHeight() != (block.getBlockHeight() - 1))) {

                    BalanceList balanceList = balanceListMap.get(block.getBlockHeight());
                    if (balanceList == null) {
                        successful = false;
                    } else {
                        randomAccessFile.write(balanceList.getBytes());
                    }
                }
            }

            // Close the file.
            randomAccessFile.close();
        } catch (Exception ignored) {
            successful = false;
        }

        // If the write was successful, move the file to the permanent location. Otherwise, delete it.
        if (successful) {
            Path temporaryPath = Paths.get(temporaryFile.getAbsolutePath());
            Path path = Paths.get(file.getAbsolutePath());
            try {
                Files.move(temporaryPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
                successful = false;
            }
        } else {
            temporaryFile.delete();
        }

        return successful;
    }

    public static void freezeBlock(Block block) {

        Block previousBlock = frozenBlockForHeight(block.getBlockHeight() - 1);
        if (previousBlock != null) {
            BalanceList balanceList = BalanceListManager.balanceListForBlock(block);
            freezeBlock(block, previousBlock.getHash(), balanceList, null);
        } else {
            System.out.println("X:Previous block is null "+ block.getBlockHeight());
        }
    }

    public static synchronized void freezeBlock(Block block, byte[] previousBlockHash, BalanceList balanceList,
                                                List<ByteBuffer> cycleVerifiers) {

        // Only continue if the block's previous hash is correct and the balance list is available.
        if (ByteUtil.arraysAreEqual(previousBlockHash, block.getPreviousBlockHash()) && balanceList != null) {
            if (!ByteUtil.arraysAreEqual(balanceList.getHash(), block.getBalanceListHash())) {
                System.out.println("X:!!BLHash mismatch "+ PrintUtil.compactPrintByteArray(balanceList.getHash()) + " vs block " + block) ;
            } else {
                try {
                    setFrozenEdge(block, cycleVerifiers);
                    BalanceListManager.updateFrozenEdge(balanceList);

                    writeBlocksToFile(Collections.singletonList(block), Collections.singletonList(balanceList),
                            individualFileForBlockHeight(block.getBlockHeight()));

                    if (block.getBlockHeight() == 0L) {

                        genesisBlockStartTimestamp = block.getStartTimestamp();
                        completedInitialization.set(true);
                    }

                } catch (Exception reportOnly) {
                    reportOnly.printStackTrace();
                    System.err.println("exception writing block to file " + reportOnly.getMessage());
                    System.out.println("X:exception writing block to file " + reportOnly.getMessage());
                }
            }
        } else {
            if (balanceList == null) {
                 System.out.println("X:BalanceList is null");
            }
            if (!ByteUtil.arraysAreEqual(previousBlockHash, block.getPreviousBlockHash())) {
                System.out.println("X:Hashes mismatch "+ PrintUtil.compactPrintByteArray(previousBlockHash) + " vs block " + block.getPreviousBlock());
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

        // Try to first load the block from the individual file. If the block is not there, extract the consolidated
        // file and try to load the block from there. In time, no consolidated files should need to be read, but this
        // provides a smooth transition from the old, more aggressive behavior of the file consolidator.
        Block block = loadBlockFromIndividualFile(blockHeight);
        if (block == null) {
            extractConsolidatedFile(consolidatedFileForBlockHeight(blockHeight));
            block = loadBlockFromIndividualFile(blockHeight);
        }
        return block;
    }

    public static void initialize() {

        if (!startedInitialization.getAndSet(true)) {

            // This method only needs to load the locally stored blocks, and it can do so synchronously.

            // Store the start timestamp so we can see how long initialization takes.
            long initializationStartTimestamp = System.currentTimeMillis();

            // Ensure that both the block directory and the individual block directory exist. The individual block
            // directory is a subdirectory of the block directory, so a single call can ensure both.
            individualBlockDirectory.mkdirs();

            // Try to load the Genesis block from file.
            Block genesisBlock = loadBlockFromFile(0L);
            if (genesisBlock != null) {

                // Set the frozen edge height to the Genesis block level.
                genesisBlockStartTimestamp = genesisBlock.getStartTimestamp();
                setFrozenEdge(genesisBlock, null);

                // Try to load the highest block that has not yet been consolidated.
                long highestIndividualFileHeight = findHighestIndividualFileHeight();
                if (highestIndividualFileHeight > getFrozenEdgeHeight()) {

                    File individualFile = individualFileForBlockHeight(highestIndividualFileHeight);
                    List<Block> individualBlockList = loadBlocksInFile(individualFile, highestIndividualFileHeight,
                            highestIndividualFileHeight);
                    if (individualBlockList.size() > 0) {
                        Block block = individualBlockList.get(0);
                        setFrozenEdge(block, null);
                        System.out.println("set frozen edge to " + block.getBlockHeight() + " in individual loading");
                    }
                }

                // Step back in the chain until the cycle information for the frozen edge can be calculated.
                long blockHeight = getFrozenEdgeHeight();
                Block frozenEdge = frozenBlockForHeight(blockHeight);
                boolean foundBreak = false;
                while (frozenEdge.getCycleInformation() == null && !foundBreak) {
                    blockHeight--;
                    Block block = frozenBlockForHeight(blockHeight);
                    if (block == null) {
                        foundBreak = true;
                    }
                }

                // Load the balance lists of the frozen edge into the balance list manager.
                BalanceList frozenEdgeBalanceList = loadBalanceListFromFileForHeight(getFrozenEdgeHeight());
                BalanceListManager.updateFrozenEdge(frozenEdgeBalanceList);

                // Mark that initialization has completed.
                completedInitialization.set(true);
                LogUtil.println(String.format("completed BlockManager initialization, elapsed %.2fs",
                        (System.currentTimeMillis() - initializationStartTimestamp) / 1000.0));
            }
        }
    }

    public static BalanceList loadBalanceListFromFileForHeight(long blockHeight) {

        BalanceList balanceList = loadBalanceListFromFile(individualFileForBlockHeight(blockHeight), blockHeight);
        if (balanceList == null) {
            extractConsolidatedFile(consolidatedFileForBlockHeight(blockHeight));

            balanceList = loadBalanceListFromFile(individualFileForBlockHeight(blockHeight), blockHeight);
        }

        return balanceList;
    }

    private static void extractConsolidatedFile(File file) {

        // This method will stay in the code because it doesn't do any harm, but it is a migration method, and it will
        // be used less and less over time. The old behavior of the file consolidator would consolidate files as soon
        // as they fell behind the frozen edge. This slowed down restarts, as consolidated files had to be read
        // directly.

        if (file.exists()) {
            System.out.println("extracting consolidated file: " + file);

            Path path = Paths.get(file.getAbsolutePath());
            try {
                byte[] fileBytes = Files.readAllBytes(path);
                ByteBuffer buffer = ByteBuffer.wrap(fileBytes);
                int numberOfBlocks = buffer.getShort();
                Block previousBlock = null;
                BalanceList previousBalanceList = null;

                System.out.println("need to extract " + numberOfBlocks + " blocks");

                for (int i = 0; i < numberOfBlocks; i++) {

                    // Read the block.
                    Block block = Block.fromByteBuffer(buffer, false);

                    // Derive the balance list, if possible. Otherwise, read it.
                    BalanceList balanceList;
                    if (previousBlock != null && block.getBlockHeight() == previousBlock.getBlockHeight() + 1L) {
                        balanceList = Block.balanceListForNextBlock(previousBlock, previousBalanceList,
                                block.getTransactions(), block.getVerifierIdentifier(), block.getBlockchainVersion());
                    } else {
                        System.out.println("reading balance list for height " + block.getBlockHeight());
                        balanceList = BalanceList.fromByteBuffer(buffer);
                    }

                    // Confirm that the balance list hash matches.
                    if (!ByteUtil.arraysAreEqual(balanceList.getHash(), block.getBalanceListHash())) {
                        throw new RuntimeException("balance list hash does not match for block " +
                                block.getBlockHeight());
                    }

                    // Write the individual file.
                    writeBlocksToFile(Collections.singletonList(block), Collections.singletonList(balanceList),
                            individualFileForBlockHeight(block.getBlockHeight()));

                    // Store the block and balance list for the next iteration.
                    previousBlock = block;
                    previousBalanceList = balanceList;
                }
            } catch (Exception e) {
                System.out.println("problem extracting consolidated file: " + e.getMessage());
            }
        }
    }

    public static BalanceList loadBalanceListFromFile(File file, long blockHeight) {

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
                    Block block = Block.fromByteBuffer(buffer, false);
                    if (previousBlock == null || (previousBlock.getBlockHeight() != block.getBlockHeight() - 1)) {
                        balanceList = BalanceList.fromByteBuffer(buffer);
                    } else {
                        balanceList = Block.balanceListForNextBlock(previousBlock, balanceList, block.getTransactions(),
                                block.getVerifierIdentifier(), block.getBlockchainVersion());
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

    public static synchronized void setFrozenEdge(Block block, List<ByteBuffer> cycleVerifiers) {

        // Freezing a block under the frozen edge is not allowed.
        if (block.getBlockHeight() < frozenEdgeHeight) {
            System.err.println("Attempting to set highest block frozen to a lesser value than is currently set.");
        } else {
            // Set the frozen and trailing edge heights. If the cycle information is null, set the trailing edge to
            // invalid.
            frozenEdge = block;
            frozenEdgeHeight = block.getBlockHeight();
            boolean isNewVerifier = false;
            if (block.getCycleInformation() == null) {
                trailingEdgeHeight = -1L;
            } else {
                trailingEdgeHeight = Math.max(block.getCycleInformation().getDeterminationHeight(), 0);
                isNewVerifier = block.getCycleInformation().isNewVerifier();
            }

            updateVerifiersInCurrentCycle(block, cycleVerifiers, isNewVerifier);
            BlockchainMetricsManager.registerBlock(block);
            MetadataManager.registerBlock(block);
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

        // A block is considered open for processing 1.5 seconds after it completes. For registration, we reduce the
        // offset to 0.5 seconds to avoid rejecting blocks due to minor clock differences.
        long offset = Block.blockDuration + (forRegistration ? 500L : 1500L);

        return genesisBlockStartTimestamp > 0 ?
                ((System.currentTimeMillis() - offset - genesisBlockStartTimestamp) / Block.blockDuration) : -1;
    }

    public static boolean inGenesisCycle() {

        return inGenesisCycle;
    }

    public static int currentCycleLength() {

        return currentCycleList.size();
    }

    public static List<ByteBuffer> verifiersInCurrentCycleList() {

        return currentCycleList;
    }

    public static Set<ByteBuffer> verifiersInCurrentCycleSet() {

        return currentCycleSet;
    }

    public static Set<Node> getCurrentAndNearCycleNodes() {

        return currentAndNearCycleNodes;
    }

    public static boolean verifierInCurrentCycle(ByteBuffer identifier) {

        return BlockManager.inGenesisCycle() || currentCycleSet.contains(identifier);
    }

    public static boolean verifierInOrNearCurrentCycle(ByteBuffer identifier) {

        return BlockManager.inGenesisCycle() || currentAndNearCycleSet.contains(identifier);
    }

    private static synchronized void updateVerifiersInCurrentCycle(Block block,
                                                                   List<ByteBuffer> bootstrapCycleVerifiers,
                                                                   boolean isNewVerifier) {

        // Store this now before we step back in the chain.
        ByteBuffer edgeIdentifierBuffer = ByteBuffer.wrap(block.getVerifierIdentifier());
        long edgeHeight = block.getBlockHeight();

        // Perform the standard cycle calculation using blocks.
        boolean foundCycle = false;
        List<ByteBuffer> currentCycleList = new ArrayList<>();
        boolean inGenesisCycle = false;
        while (block != null && !foundCycle) {

            ByteBuffer identifierBuffer = ByteBuffer.wrap(block.getVerifierIdentifier());
            if (currentCycleList.contains(identifierBuffer)) {
                foundCycle = true;
            } else {
                currentCycleList.add(0, identifierBuffer);
            }

            inGenesisCycle = block.getBlockHeight() == 0 && !foundCycle;
            block = block.getPreviousBlock();
        }

        // If we are in the Genesis cycle (we hit block 0), mark that we also found the cycle.
        if (inGenesisCycle) {
            foundCycle = true;
        }

        if (block == null && !foundCycle) {

            // Get the alternate cycle list. If we are extending a complete cycle, we can use the current cycle as a
            // basis. Otherwise, we can only use a provided bootstrap cycle.
            List<ByteBuffer> alternateCycleList = null;
            if (edgeHeight == currentCycleEndHeight + 1 && cycleComplete) {
                alternateCycleList = new ArrayList<>(BlockManager.currentCycleList);

                // Remove the up to the current verifier, if present.
                int indexOfVerifierInPreviousCycle = alternateCycleList.indexOf(edgeIdentifierBuffer);
                for (int i = 0; i <= indexOfVerifierInPreviousCycle; i++) {
                    alternateCycleList.remove(0);
                }

                // Add the current verifier.
                alternateCycleList.add(edgeIdentifierBuffer);

            } else if (bootstrapCycleVerifiers != null) {

                // Use the provided cycle without modification.
                alternateCycleList = new ArrayList<>(bootstrapCycleVerifiers);
            }

            if (alternateCycleList == null) {
                // Both calculations failed.
                cycleComplete = false;
            } else {
                // The alternate calculation succeeded.
                currentCycleList = alternateCycleList;
                cycleComplete = true;
            }
        } else {
            // The standard calculation succeeded.
            cycleComplete = true;
        }

        if (cycleComplete) {

            // If this is a new verifier and the height is greater than the previous value of lastVerifierJoinHeight,
            // store the height. This is used to cheaply determine whether new verifiers are eligible to join. The
            // greater-than condition is used to avoid issues that may arise during initialization.
            if (isNewVerifier && edgeHeight > lastVerifierJoinHeight) {
                lastVerifierJoinHeight = edgeHeight;
                PersistentData.put(lastVerifierJoinHeightKey, lastVerifierJoinHeight);
            }

            // If a verifier was dropped from the cycle, store the height. This is used to determine whether to
            // penalize poorly performing verifiers, as we do not want to drop verifiers from the cycle too quickly.
            if (currentCycleList.size() < BlockManager.currentCycleList.size() ||
                    (currentCycleList.size() == BlockManager.currentCycleList.size() && isNewVerifier)) {
                lastVerifierRemovalHeight = edgeHeight;
                PersistentData.put(lastVerifierRemovalHeightKey, lastVerifierRemovalHeight);
            }

            // Store the edge height, cycle list, and indication of Genesis cycle.
            BlockManager.currentCycleEndHeight = edgeHeight;
            BlockManager.currentCycleList = currentCycleList;
            BlockManager.inGenesisCycle = inGenesisCycle;

            // Build the cycle set.
            Set<ByteBuffer> currentCycleSet = ConcurrentHashMap.newKeySet();
            currentCycleSet.addAll(currentCycleList);
            BlockManager.currentCycleSet = currentCycleSet;

            // Build the cycle-and-near set.
            Set<ByteBuffer> currentAndNearCycleSet = ConcurrentHashMap.newKeySet();
            currentAndNearCycleSet.addAll(currentCycleList);
            ByteBuffer topNewVerifier = NewVerifierVoteManager.topVerifier();
            if (topNewVerifier != null) {
                currentAndNearCycleSet.add(topNewVerifier);
            }
            BlockManager.currentAndNearCycleSet = currentAndNearCycleSet;

            // Build the cycle-and-near node set.
            Set<Node> currentAndNearCycleNodes = ConcurrentHashMap.newKeySet();
            for (Node node : NodeManager.getMesh()) {
                if (currentAndNearCycleSet.contains(ByteBuffer.wrap(node.getIdentifier()))) {
                    currentAndNearCycleNodes.add(node);
                }
            }
            BlockManager.currentAndNearCycleNodes = currentAndNearCycleNodes;
        }
    }

    public static boolean isCycleComplete() {

        return cycleComplete;
    }
}
