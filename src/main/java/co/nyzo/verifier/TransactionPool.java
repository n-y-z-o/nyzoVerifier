package co.nyzo.verifier;

import java.nio.ByteBuffer;
import java.util.*;

public class TransactionPool {

    private static long frozenEdgeHeight = 1L;
    private static final Map<Long, Map<ByteBuffer, Transaction>> transactions = new HashMap<>();

    public static synchronized void addTransaction(Transaction transaction) {

        long transactionBlockHeight = BlockManager.heightForTimestamp(transaction.getTimestamp());
        if (transactionBlockHeight > frozenEdgeHeight) {
            Map<ByteBuffer, Transaction> transactionsForHeight = transactions.get(transactionBlockHeight);
            if (transactionsForHeight == null) {
                transactionsForHeight = new HashMap<>();
                transactions.put(transactionBlockHeight, transactionsForHeight);
            }
            transactionsForHeight.put(ByteBuffer.wrap(transaction.getSignature()), transaction);
        }
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

        long newFrozenEdgeHeight = BlockManager.frozenEdgeHeight();
        if (newFrozenEdgeHeight > frozenEdgeHeight) {
            frozenEdgeHeight = newFrozenEdgeHeight;
            for (Long height : new HashSet<>(transactions.keySet())) {
                if (height <= frozenEdgeHeight) {
                    transactions.remove(height);
                }
            }
        }
    }
}
