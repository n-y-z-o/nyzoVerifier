package co.nyzo.verifier.util;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.ClientTransactionUtil;
import co.nyzo.verifier.messages.BlockVote;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public class ConsensusTracker {

    // This class will store records of every block received and every vote change for each block. This should only be
    // used on systems with large amounts of storage available, and it will automatically stop tracking when the usable
    // space on the drive falls below the specified threshold. Also, tracking is not performed for any run mode other
    // than Verifier.

    private static final File rootDirectory = new File(Verifier.dataRootDirectory, "consensus_tracker");
    private static boolean enableTracker = PreferencesUtil.getBoolean("enable_consensus_tracker", false);
    private static final long terminationStorageThreshold =
            PreferencesUtil.getLong("consensus_tracker_storage_threshold_bytes", 20_000_000_000L);  // 20 GB.
    private static final int eventsPerFile = 10000;

    private static long frozenEdge = -1L;
    private static Map<Long, Set<ConsensusEvent>> events = new ConcurrentHashMap<>();

    public static void register(long height, Object object) {
        // Only register the event if the tracker is enabled, the run mode is verifier, the object is valid, and the
        // height is in a reasonable range.
        if (enableTracker && RunMode.getRunMode() == RunMode.Verifier && object != null && height >= frozenEdge - 3 &&
                height < frozenEdge + 5) {

            // Register the event in the set for the appropriate height.
            Set<ConsensusEvent> eventsForHeight = events.merge(height, new HashSet<>(),
                    new BiFunction<Set<ConsensusEvent>, Set<ConsensusEvent>, Set<ConsensusEvent>>() {
                        @Override
                        public Set<ConsensusEvent> apply(Set<ConsensusEvent> consensusEvents1,
                                                         Set<ConsensusEvent> consensusEvents2) {
                            Set<ConsensusEvent> result;
                            if (consensusEvents1 == null) {
                                result = consensusEvents2;
                            } else if (consensusEvents2 == null) {
                                result = consensusEvents1;
                            } else {
                                result = consensusEvents1;
                                consensusEvents1.addAll(consensusEvents2);
                            }

                            return result;
                        }
                    });
            eventsForHeight.add(new ConsensusEvent(height, object));

            // If the events set for the height has gotten too large, perform maintenance.
            if (eventsForHeight.size() > eventsPerFile) {
                performMaintenance();
            }
        }
    }

    public static void performMaintenance() {
        if (enableTracker) {
            // If the usable space has fallen below the termination threshold, disable tracking.
            long usableSpace = getUsableSpace();
            if (usableSpace < terminationStorageThreshold) {
                LogUtil.println("disabling consensus tracker because usable space, " + usableSpace +
                        " is less than threshold " + terminationStorageThreshold);
                enableTracker = false;
            }

            // Update the frozen edge.
            frozenEdge = BlockManager.getFrozenEdgeHeight();

            // Look through all heights in the map. If any are over the size threshold or below the height at which
            // events are saved, remove them from the map and write them to file. Iterate over a new set of heights,
            // because this loop modifies the map.
            synchronized (ConsensusTracker.class) {
                for (Long height : new HashSet<>(events.keySet())) {
                    Set<ConsensusEvent> eventsForHeight = events.get(height);
                    if (height < frozenEdge - 4 || eventsForHeight.size() > eventsPerFile) {
                        events.remove(height);
                        writeFile(height, eventsForHeight);
                    }
                }
            }
        } else {
            // Empty the set, in case there are any lingering items. If tracking has been disabled, no further files
            // will be written, so there is no reason to continue keeping the events in memory.
            events.clear();
        }
    }

    private static void writeFile(long height, Set<ConsensusEvent> events) {

        // Start a thread to write the file. We do not want the time used to write the file to prevent the verifier
        // from continuing to the next block.
        new Thread(new Runnable() {
            @Override
            public void run() {

                // Get the file path. The check of file existence is not truly concurrency-safe, but it is highly
                // unlikely to fail in practice.
                File file = null;
                int fileIndex = 0;
                while (file == null || file.exists()) {
                    file = new File(rootDirectory, String.format("consensus_%08d_%04d", height, fileIndex++));
                }

                // Create the list and sort on timestamp.
                List<ConsensusEvent> eventList = new ArrayList<>(events);
                eventList.sort(new Comparator<ConsensusEvent>() {
                    @Override
                    public int compare(ConsensusEvent event1, ConsensusEvent event2) {
                        return Long.compare(event1.getTimestamp(), event2.getTimestamp());
                    }
                });

                // Determine the number of votes for each hash. If only one block received votes, the block votes will
                // not be written separately to the file.
                Map<ByteBuffer, Integer> blockVoteCounts = new HashMap<>();
                for (ConsensusEvent event : eventList) {
                    if (event.getData() instanceof BlockVote) {
                        BlockVote blockVote = (BlockVote) event.getData();
                        ByteBuffer hash = ByteBuffer.wrap(blockVote.getHash());
                        blockVoteCounts.put(hash, blockVoteCounts.getOrDefault(hash, 0) + 1);
                    }
                }

                // Format the events for the file.
                List<String> fileContents = new ArrayList<>();
                for (ConsensusEvent event : eventList) {
                    if (event.getData() instanceof BlockVote) {
                        if (blockVoteCounts.size() > 1) {
                            BlockVote blockVote = (BlockVote) event.getData();
                            fileContents.add(PrintUtil.compactPrintTimestamp(event.getTimestamp()) + ":" + blockVote);
                        }
                    } else if (event.getData() instanceof Block) {

                        Block block = (Block) event.getData();
                        String blockLine = PrintUtil.compactPrintTimestamp(event.getTimestamp()) + ":" + block;

                        // If the verifier included a transaction in the block, add its sender data to the line.
                        Transaction verifierTransaction = null;
                        for (Transaction transaction : block.getTransactions()) {
                            if (ByteUtil.arraysAreEqual(transaction.getSenderIdentifier(),
                                    block.getVerifierIdentifier())) {
                                verifierTransaction = transaction;
                            }
                        }
                        if (verifierTransaction != null) {
                            blockLine += ",transaction data=" +
                                    ClientTransactionUtil.senderDataString(verifierTransaction.getSenderData());
                        }

                        // Add the line to the list.
                        fileContents.add(blockLine);
                    } else {
                        fileContents.add(PrintUtil.compactPrintTimestamp(event.getTimestamp()) + ":" + event.getData());
                    }
                }

                // Add raw vote counts.
                for (ByteBuffer hash : blockVoteCounts.keySet()) {
                    fileContents.add("hash=" + PrintUtil.compactPrintByteArray(hash.array()) + ",count=" +
                            blockVoteCounts.get(hash));
                }

                // Write the file.
                FileUtil.writeFile(Paths.get(file.getAbsolutePath()), fileContents);
            }
        }).start();
    }

    private static long getUsableSpace() {

        // Ensure that the directory exists.
        rootDirectory.mkdirs();

        // Get the usable space.
        long freeSpace = 0L;
        Path path = Paths.get(rootDirectory.getAbsolutePath());
        try {
            FileStore store = Files.getFileStore(path);
            freeSpace = store.getUsableSpace();
        } catch (Exception ignored) { }

        return freeSpace;
    }
}
