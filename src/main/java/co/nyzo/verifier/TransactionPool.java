package co.nyzo.verifier;

import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TransactionPool {

    private static long frozenEdgeHeight = 1L;
    private static final Map<Long, Map<ByteBuffer, Transaction>> transactions = new ConcurrentHashMap<>();
    private static Map<ByteBuffer, Integer> senderCountMap = new ConcurrentHashMap<>();

    private static final int maximumTransactionsInPoolPerSender = 100;
    private static final long maximumBlocksInFutureAccepted = 12343L;  // one day

    public static synchronized boolean addTransaction(Transaction transaction, StringBuilder error,
                                                      StringBuilder warning) {

        // Ensure the error and warning string builders are not null to simplify later logic.
        if (error == null) {
            error = new StringBuilder();
        }
        if (warning == null) {
            warning = new StringBuilder();
        }

        boolean addedToPool;
        long transactionBlockHeight = BlockManager.heightForTimestamp(transaction.getTimestamp());
        long maximumAcceptedHeight = BlockManager.openEdgeHeight(true) + maximumBlocksInFutureAccepted;
        if (transactionBlockHeight <= frozenEdgeHeight) {
            addedToPool = false;
            error.append("The block height of the transaction, ").append(transactionBlockHeight)
                    .append(", is at or behind the frozen edge, ").append(frozenEdgeHeight).append(". ");
        } else if (transactionBlockHeight > maximumAcceptedHeight) {
            addedToPool = false;
            error.append("The block height for the transaction, ").append(transactionBlockHeight)
                    .append(", is past the height for which transactions are currently being accepted, ")
                    .append(maximumAcceptedHeight).append(". ");
        } else {

            // Only add the transaction if the sender is known to the system.
            if (BalanceListManager.accountIsInSystem(transaction.getSenderIdentifier())) {

                // Get the map of transactions for the height. Make the map, if necessary.
                Map<ByteBuffer, Transaction> transactionsForHeight = transactions.get(transactionBlockHeight);
                if (transactionsForHeight == null) {
                    transactionsForHeight = new HashMap<>();
                    transactions.put(transactionBlockHeight, transactionsForHeight);
                }

                // If this is a new transaction and the sender has not exceeded their limit, add this transaction.
                ByteBuffer senderIdentifier = ByteBuffer.wrap(transaction.getSenderIdentifier());
                ByteBuffer signature = ByteBuffer.wrap(transaction.getSignature());
                if (transactionsForHeight.keySet().contains(signature)) {
                    addedToPool = true;
                    warning.append("This transaction was already in the system. ");
                } else if (senderCountMap.getOrDefault(senderIdentifier, 0) >= maximumTransactionsInPoolPerSender) {
                    addedToPool = false;
                    error.append("This sender has too many transactions currently waiting to be processed. ");
                } else {

                    addedToPool = true;

                    // Increment the count for the sender.
                    senderCountMap.put(senderIdentifier, senderCountMap.getOrDefault(senderIdentifier, 0) + 1);

                    // Put the transaction in the map for the height.
                    transactionsForHeight.put(signature, transaction);
                }
            } else {
                addedToPool = false;
                error.append("This sender was not found in the system. ");
            }
        }

        return addedToPool;
    }

    public static synchronized List<Transaction> transactionsForHeight(long blockHeight) {

        List<Transaction> transactionsForHeight = new ArrayList<>();
        Map<ByteBuffer, Transaction> transactionMapForHeight = transactions.get(blockHeight);
        if (transactionMapForHeight != null) {
            transactionsForHeight.addAll(transactionMapForHeight.values());
        }

        return transactionsForHeight;
    }

    public static synchronized List<Transaction> allTransactions() {

        List<Transaction> allTransactions = new ArrayList<>();
        for (Map<ByteBuffer, Transaction> transactionsForHeight : transactions.values()) {
            allTransactions.addAll(transactionsForHeight.values());
        }

        return allTransactions;
    }

    public static synchronized int transactionPoolSize() {

        int numberOfTransactions = 0;
        for (Map<ByteBuffer, Transaction> transactionsForHeight : transactions.values()) {
            numberOfTransactions += transactionsForHeight.size();
        }

        return numberOfTransactions;
    }

    public static synchronized void updateFrozenEdge() {

        long newFrozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
        if (newFrozenEdgeHeight > frozenEdgeHeight) {
            frozenEdgeHeight = newFrozenEdgeHeight;
            for (Long height : new HashSet<>(transactions.keySet())) {
                if (height <= frozenEdgeHeight) {
                    transactions.remove(height);
                }
            }
        }

        // Instead of decrementing the counters for each sender as transactions are removed, the count map is rebuilt
        // here. This is a more robust solution that leaves the transaction map as the sole long-term authority on
        // transactions.
        Map<ByteBuffer, Integer> senderCountMap = new ConcurrentHashMap<>();
        for (Long height : transactions.keySet()) {
            for (Transaction transaction : transactions.get(height).values()) {
                ByteBuffer senderIdentifier = ByteBuffer.wrap(transaction.getSenderIdentifier());
                senderCountMap.put(senderIdentifier, senderCountMap.getOrDefault(senderIdentifier, 0) + 1);
            }
        }

        // Assign the new map to the class variable.
        TransactionPool.senderCountMap = senderCountMap;
    }
}
