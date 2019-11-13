package co.nyzo.verifier;

import co.nyzo.verifier.util.TestnetUtil;
import co.nyzo.verifier.util.ThreadUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SeedTransactionManager {

    public static final File rootDirectory = new File(Verifier.dataRootDirectory, "seed_transactions");

    public static final long blocksPerFile = 10000L;

    public static final long transactionsPerYear = (60L * 60L * 24L * 365L * 1000L + Block.blockDuration - 1) /
            Block.blockDuration;  // round up
    public static final long totalSeedTransactions = TestnetUtil.testnet ? 40000 :
            transactionsPerYear * 6L;  // 40k testnet, six years production
    public static final long lowestSeedTransactionHeight = 2;  // start at block 2
    public static final long highestSeedTransactionHeight = lowestSeedTransactionHeight + totalSeedTransactions - 1;

    private static final Map<Long, Transaction> transactionMap = new HashMap<>();

    private static final AtomicBoolean alive = new AtomicBoolean(false);

    public static boolean isAlive() {
        return alive.get();
    }

    public static void start() {

        if (!alive.getAndSet(true)) {

            new Thread(new Runnable() {
                @Override
                public void run() {

                    while (!UpdateUtil.shouldTerminate() &&
                            BlockManager.getFrozenEdgeHeight() < highestSeedTransactionHeight) {

                        long currentFileIndex = BlockManager.getFrozenEdgeHeight() / blocksPerFile;

                        // Check if we have transactions for the next 20 blocks (100 seconds). If we do, we can skip
                        // the rest of the process for this iteration.
                        boolean haveBlocks = true;
                        for (int i = 0; i < 20 && haveBlocks; i++) {
                            long height = BlockManager.getFrozenEdgeHeight() + i + 1;
                            if (height >= lowestSeedTransactionHeight && height <= highestSeedTransactionHeight &&
                                    transactionMap.get(height) == null) {
                                haveBlocks = false;
                            }
                        }

                        if (!haveBlocks) {

                            // Ensure that we have both the current file and the next file and load them into memory.
                            for (long fileIndex = currentFileIndex; fileIndex < currentFileIndex + 2; fileIndex++) {
                                File file = fileForIndex(fileIndex);
                                if (!file.exists()) {
                                    fetchFile(file);
                                }
                                loadFile(file);
                            }

                            // If the previous file exists, delete it.
                            File previousFile = fileForIndex(currentFileIndex - 1);
                            if (previousFile.exists()) {
                                previousFile.delete();
                            }

                            // Remove any items from the map below the frozen edge.
                            Set<Long> keys = new HashSet<>(transactionMap.keySet());
                            for (Long key : keys) {
                                if (key < BlockManager.getFrozenEdgeHeight()) {
                                    transactionMap.remove(key);
                                }
                            }
                        }

                        // Sleep for 30 seconds, checking periodically if we should allow the thread to exit.
                        for (int i = 0; i < 15 && !UpdateUtil.shouldTerminate(); i++) {
                            ThreadUtil.sleep(2000L);
                        }
                    }

                    System.out.println("exiting SeedTransactionManager thread");
                    alive.set(false);
                }
            }, "SeedTransactionManager").start();
        }
    }

    private static File fileForIndex(long index) {

        return new File(rootDirectory, String.format("%06d.nyzotransaction", index));
    }

    public static String urlForFile(String filename) {

        return TestnetUtil.testnet ? "https://testnet.nyzo.co/seed/" + filename :
                "https://nyzo-transactions.nyc3.digitaloceanspaces.com/" + filename;
    }

    public static void fetchFile(File file) {

        try {

            file.getParentFile().mkdirs();

            URL url = new URL(urlForFile(file.getName()));
            ReadableByteChannel channel = Channels.newChannel(url.openStream());
            FileOutputStream outputStream = new FileOutputStream(file);
            outputStream.getChannel().transferFrom(channel, 0, Long.MAX_VALUE);
            channel.close();
            outputStream.close();

        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
    }

    private static void loadFile(File file) {

        Path path = Paths.get(file.getAbsolutePath());
        boolean successful;
        try {
            byte[] fileBytes = Files.readAllBytes(path);
            ByteBuffer byteBuffer = ByteBuffer.wrap(fileBytes);
            int numberOfTransactions = byteBuffer.getInt();

            // Read the transactions. Only add transactions with valid heights and signatures to the map.
            Map<Long, Transaction> transactionsInFile = new HashMap<>();
            for (int i = 0; i < numberOfTransactions; i++) {
                long height = byteBuffer.getLong();
                Transaction transaction = Transaction.fromByteBuffer(byteBuffer);
                if (height > 0 && transaction.signatureIsValid()) {
                    transactionsInFile.put(height, transaction);
                }
            }

            successful = transactionsInFile.size() == numberOfTransactions;
            if (successful) {
                transactionMap.putAll(transactionsInFile);
            }
        } catch (Exception e) {
            successful = false;
        }

        if (!successful) {
            // If we have any problems at all, delete the file so it can be downloaded again on the next pass.
            try {
                file.delete();
            } catch (Exception ignored) { }
        }
    }

    public static Transaction transactionForBlock(long blockHeight) {

        // This method will only return one transaction per block. There is not an explicit limitation on one seed
        // transaction per block, but we are only generating one per block in the initial seed transactions. So,
        // this is only a simplification based on our data, not a limitation of the system, and other seed transactions
        // could potentially be passed around the network.
        return transactionMap.get(blockHeight);
    }


}
