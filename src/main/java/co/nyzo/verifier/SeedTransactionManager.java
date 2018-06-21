package co.nyzo.verifier;

import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class SeedTransactionManager {

    public static final long blocksPerFile = 1000L;
    private static long lastBlockRequested = 0L;

    public static final long blocksPerDay = 12 * 60 * 24;
    public static final long startHeight = 5;  // start at block 5
    public static final long transactionsPerYear = blocksPerDay * 365L;  // one year of seed transactions
    public static final long totalSeedTransactions = blocksPerDay * 30L; // transactionsPerYear * 5L + blocksPerDay * 30L;  // five years
    public static final long highestSeedHeight = startHeight + totalSeedTransactions;

    private static final Map<Long, Transaction> transactionMap = new HashMap<>();

    public static void start() {

        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    Thread.sleep(100L);
                } catch (Exception e) { }

                while (!UpdateUtil.shouldTerminate() &&
                        lastBlockRequested < highestSeedHeight + blocksPerFile * 3L) {

                    long currentFileIndex = lastBlockRequested / blocksPerFile;

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

                    // Remove any items from the map below the last-requested height.
                    Set<Long> keys = new HashSet<>(transactionMap.keySet());
                    for (Long key : keys) {
                        if (key < lastBlockRequested) {
                            transactionMap.remove(key);
                        }
                    }

                    // Sleep for 30 seconds, checking periodically if we should allow the thread to exit.
                    for (int i = 0; i < 15; i++) {
                        if (!UpdateUtil.shouldTerminate()) {
                            try {
                                Thread.sleep(2000L);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }

                System.out.println("exiting SeedTransactionManager thread");
            }
        }, "SeedTransactionManager").start();
    }

    private static File fileForIndex(long index) {

        return new File(Verifier.dataRootDirectory, String.format("%06d.nyzotransaction", index));
    }

    private static String s3UrlForFile(File file) {

        return "https://s3-us-west-2.amazonaws.com/nyzo/" + file.getName();
    }

    public static void fetchFile(File file) {

        try {

            file.getParentFile().mkdirs();

            URL url = new URL(s3UrlForFile(file));
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
        lastBlockRequested = blockHeight;
        return transactionMap.get(blockHeight);
    }


}
