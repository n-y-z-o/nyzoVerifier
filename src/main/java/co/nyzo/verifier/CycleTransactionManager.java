package co.nyzo.verifier;

import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.messages.CycleTransactionSignature;
import co.nyzo.verifier.util.FileUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

public class CycleTransactionManager {

    // This class tracks all cycle transactions. To avoid manipulation, each cycle verifier is only allowed to have one
    // suggested transaction at a time.
    private static final Map<ByteBuffer, Transaction> transactions = new ConcurrentHashMap<>();

    private static final File mapFile = new File(Verifier.dataRootDirectory, "cycle_transactions");
    private static final AtomicBoolean mapHasChanged = new AtomicBoolean(false);
    static {
        loadMap();
    }

    public static boolean registerTransaction(Transaction transaction, StringBuilder error, StringBuilder warning) {

        // Ensure the string builders are not null to simplify the proceeding logic.
        if (error == null) {
            error = new StringBuilder();
        }
        if (warning == null) {
            warning = new StringBuilder();
        }

        // Only continue if the transaction is a valid cycle transaction and it is in the future.
        boolean accepted = false;
        if (transaction == null) {
            error.append("Transaction is null.");
        } else if (transaction.getType() != Transaction.typeCycle) {
            error.append("Not a cycle transaction.");
        } else if (!transaction.signatureIsValid()) {
            error.append("Signature is invalid.");
        } else if (transaction.getTimestamp() <= System.currentTimeMillis()) {
            error.append("Timestamp is in the past.");
        } else if (!BlockManager.verifierInCurrentCycle(ByteBuffer.wrap(transaction.getSenderIdentifier()))) {
            error.append("Initiator is not in the cycle.");
        } else if (transaction.getAmount() > Transaction.maximumCycleTransactionAmount) {
            error.append("Amount is greater than maximum allowed for cycle transactions.");
        } else {

            // Check the map for an existing transaction that would take precedence over this transaction. This avoids
            // unnecessary map operations, including those that could result in signature losses for existing
            // transactions.
            ByteBuffer key = ByteBuffer.wrap(transaction.getSenderIdentifier());
            Transaction existingTransaction = transactions.get(key);
            if (existingTransaction != null &&
                    ByteUtil.arraysAreEqual(transaction.getSignature(), existingTransaction.getSignature())) {
                // Mark the transaction as accepted, but warn the sender that it is a duplicate.
                accepted = true;
                warning.append("This transaction is already registered.");
            } else if (existingTransaction != null && transaction.getTimestamp() < existingTransaction.getTimestamp()) {
                error.append("A newer transaction is already registered");
            } else {
                // Add a warning for transactions near in the future.
                if (transaction.getTimestamp() < System.currentTimeMillis() + 1000L * 60L * 60L * 24L) {
                    warning.append("Transaction is in the next 24 hours. This is a short time frame for a cycle ")
                            .append("transaction.");
                }

                // Despite the previous checks, this is still handled as a fully asynchronous process, accounting for
                // collisions, to improve thread safety.
                accepted = true;
                mapHasChanged.set(true);
                transactions.merge(ByteBuffer.wrap(transaction.getSenderIdentifier()), transaction,
                        new BiFunction<Transaction, Transaction, Transaction>() {
                            @Override
                            public Transaction apply(Transaction transaction1, Transaction transaction2) {
                                // If either input is null, take the other input. If neither is null, take the input
                                // with the later timestamp.
                                Transaction result;
                                if (transaction1 == null) {
                                    result = transaction2;
                                } else if (transaction2 == null) {
                                    result = transaction1;
                                } else if (transaction1.getTimestamp() > transaction2.getTimestamp()) {
                                    result = transaction1;
                                } else {
                                    result = transaction2;
                                }

                                return result;
                            }
                        });
            }
        }

        return accepted;
    }

    public static boolean registerSignature(CycleTransactionSignature signature) {

        // If the transaction exists, try to add the signature to it. The addSignature() method checks the signature's
        // validity and cycle membership.
        boolean registered = false;
        Transaction transaction = transactions.get(ByteBuffer.wrap(signature.getTransactionInitiator()));
        if (transaction != null && BlockManager.verifierInCurrentCycle(ByteBuffer.wrap(signature.getIdentifier())) &&
            transaction.addSignature(signature.getIdentifier(), signature.getSignature())) {
            registered = true;
            mapHasChanged.set(true);
        }

        return registered;
    }

    public static Collection<Transaction> getTransactions() {
        return transactions.values();
    }

    public static List<Transaction> transactionsForHeight(long blockHeight) {

        List<Transaction> transactionsForHeight = new ArrayList<>();
        for (Transaction transaction : transactions.values()) {
            if (BlockManager.heightForTimestamp(transaction.getTimestamp()) == blockHeight) {
                transactionsForHeight.add(transaction);
            }
        }

        return transactionsForHeight;
    }

    public static void performMaintenance() {

        // Remove transactions at or below the new frozen edge.
        long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
        for (ByteBuffer identifier : new HashSet<>(transactions.keySet())) {
            Transaction transaction = transactions.get(identifier);
            if (transaction != null) {
                long height = BlockManager.heightForTimestamp(transaction.getTimestamp());
                if (height <= frozenEdgeHeight) {
                    transactions.remove(identifier);
                    mapHasChanged.set(true);
                }
            }
        }

        if (mapHasChanged.getAndSet(false)) {
            persistMap();
        }
    }

    private static void persistMap() {

        // To ensure that the transactions do not gain signatures between size calculation and production of the byte
        // arrays, the byte arrays are retrieved first and then assembled into one large array.
        int byteSize = FieldByteSize.unnamedInteger;  // number of transactions is stored as a 4-byte integer
        List<byte[]> byteArrayList = new ArrayList<>();
        for (Transaction transaction : transactions.values()) {
            byte[] byteArray = transaction.getBytes();
            byteArrayList.add(byteArray);
            byteSize += byteArray.length;
        }

        // Assemble the final array.
        byte[] assembledArray = new byte[byteSize];
        ByteBuffer buffer = ByteBuffer.wrap(assembledArray);
        buffer.putInt(byteArrayList.size());
        for (byte[] byteArray : byteArrayList) {
            buffer.put(byteArray);
        }

        // Write the file. This is an atomic operation.
        FileUtil.writeFile(Paths.get(mapFile.getAbsolutePath()), assembledArray);
    }

    private static void loadMap() {

        // Get the byte array.
        byte[] fileBytes = null;
        try {
            fileBytes = Files.readAllBytes(Paths.get(mapFile.getAbsolutePath()));
        } catch (Exception ignored) { }

        if (fileBytes != null) {
            // Read the transactions from the byte array and register them with the map.
            ByteBuffer buffer = ByteBuffer.wrap(fileBytes);
            int numberOfTransactions = buffer.getInt();
            for (int i = 0; i < numberOfTransactions; i++) {
                Transaction transaction = Transaction.fromByteBuffer(buffer);
                registerTransaction(transaction, null, null);
            }
        }
    }
}
