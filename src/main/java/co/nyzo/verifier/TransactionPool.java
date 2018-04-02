package co.nyzo.verifier;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TransactionPool {

    private static final List<Transaction> transactionPool = new ArrayList<>();

    private static final File file1 = new File(Block.dataRootDirectory, "pool1.nyzotransactions");
    private static final File file2 = new File(Block.dataRootDirectory, "pool2.nyzotransactions");

    static {
        loadTransactionsFromDisk();
        System.out.println("loaded " + transactionPool.size() + " transactions from disk");
        startBackgroundThread();
    }

    public static synchronized void addTransaction(Transaction transaction) {
        transactionPool.add(transaction);
    }

    public static synchronized List<Transaction> transactionsForBlock(long blockHeight) {

        List<Transaction> transactionsForBlock = new ArrayList<>();
        long startTimestamp = BlockManager.startTimestampForHeight(blockHeight);
        long endTimestamp = BlockManager.endTimestampForHeight(blockHeight);
        synchronized (TransactionPool.class) {
            for (Transaction transaction : transactionPool) {
                if (transaction.getTimestamp() >= startTimestamp && transaction.getTimestamp() < endTimestamp) {
                    transactionsForBlock.add(transaction);
                }
            }
        }

        return transactionsForBlock;
    }

    public static synchronized List<Transaction> allTransactions() {
        return new ArrayList<>(transactionPool);
    }

    public static synchronized void removeTransactionsToHeight(long blockHeight) {

        long cutoffTimestamp = BlockManager.endTimestampForHeight(blockHeight);
        for (int i = transactionPool.size() - 1; i >= 0; i--) {
            if (transactionPool.get(i).getTimestamp() < cutoffTimestamp) {
                transactionPool.remove(i);
            }
        }
    }

    private static synchronized void loadTransactionsFromDisk() {

        loadTransactionsFromFile(file1);
        loadTransactionsFromFile(file2);
    }

    private static void loadTransactionsFromFile(File file) {

        try {

            // Read all transactions from both files, removing duplicates.
            Set<ByteBuffer> knownTransactionSignatures = new HashSet<>();
            byte[] fileBytes = Files.readAllBytes(Paths.get(file.getAbsolutePath()));
            if (fileBytes != null && fileBytes.length > 0) {
                ByteBuffer fileBuffer = ByteBuffer.wrap(fileBytes);
                int numberOfTransactions = fileBuffer.getInt();
                for (int i = 0; i < numberOfTransactions; i++) {
                    Transaction transaction = Transaction.fromByteBuffer(fileBuffer);
                    if (transaction != null) {
                        ByteBuffer transactionSignature = ByteBuffer.wrap(transaction.getSignature());
                        if (!knownTransactionSignatures.contains(transactionSignature)) {
                            knownTransactionSignatures.add(transactionSignature);
                            transactionPool.add(transaction);
                        }
                    }
                }
            }
        } catch (Exception ignored) { }
    }

    private static void startBackgroundThread() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                // Ensure the parent directory exists.
                file1.getParentFile().mkdirs();

                // Alternate writing files 1 and 2. If the server is killed between writing and deleting, the worst case
                // will be duplicates between the two files, which will be de-duplicated in the loading process.
                boolean writingFile1 = true;
                while (true) {
                    try {
                        // Determine which file to write and which to delete.
                        File fileToWrite = writingFile1 ? file1 : file2;
                        File fileToDelete = writingFile1 ? file2 : file1;
                        writingFile1 = !writingFile1;

                        // Determine the buffer size.
                        List<Transaction> transactions = allTransactions();
                        int bufferSize = 4;
                        for (Transaction transaction : transactions) {
                            bufferSize += transaction.getByteSize(false);
                        }

                        // Assemble the buffer.
                        byte[] array = new byte[bufferSize];
                        ByteBuffer buffer = ByteBuffer.wrap(array);
                        buffer.putInt(transactions.size());
                        for (Transaction transaction : transactions) {
                            buffer.put(transaction.getBytes(false));
                        }

                        // Write the new file and delete the old.
                        Files.write(Paths.get(fileToWrite.getAbsolutePath()), array);
                        fileToDelete.delete();

                        // Sleep for two seconds to give the CPU a break.
                        Thread.sleep(2000);
                    } catch (Exception reportOnly) {
                        System.err.println("exception in TransactionPool background thread: " +
                                reportOnly.getMessage());
                    }
                }
            }
        });
    }
}
