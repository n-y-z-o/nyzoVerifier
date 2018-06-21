package co.nyzo.verifier;

import co.nyzo.verifier.messages.TransactionPoolResponse;
import co.nyzo.verifier.util.IpUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class TransactionPool {

    private static long frozenEdgeHeight = 1L;
    private static final Map<ByteBuffer, Transaction> transactionPool = new HashMap<>();

    public static synchronized void addTransaction(Transaction transaction) {

        if (BlockManager.heightForTimestamp(transaction.getTimestamp()) > frozenEdgeHeight) {
            transactionPool.put(ByteBuffer.wrap(transaction.getSignature()), transaction);
        }
    }

    public static synchronized List<Transaction> transactionsForBlock(long blockHeight) {

        List<Transaction> transactionsForBlock = new ArrayList<>();
        long startTimestamp = BlockManager.startTimestampForHeight(blockHeight);
        long endTimestamp = BlockManager.endTimestampForHeight(blockHeight);
        for (Transaction transaction : transactionPool.values()) {
            if (transaction.getTimestamp() >= startTimestamp && transaction.getTimestamp() < endTimestamp) {
                transactionsForBlock.add(transaction);
            }
        }

        return transactionsForBlock;
    }

    public static synchronized List<Transaction> allTransactions() {
        return new ArrayList<>(transactionPool.values());
    }

    public static synchronized int transactionPoolSize() {
        return transactionPool.size();
    }

    public static synchronized void updateFrozenEdge() {

        long newFrozenEdgeHeight = BlockManager.frozenEdgeHeight();
        if (newFrozenEdgeHeight > frozenEdgeHeight) {
            frozenEdgeHeight = newFrozenEdgeHeight;
            long cutoffTimestamp = BlockManager.endTimestampForHeight(frozenEdgeHeight);
            List<Transaction> transactions = allTransactions();
            for (Transaction transaction : transactions) {
                if (transaction.getTimestamp() < cutoffTimestamp) {
                    transactionPool.remove(ByteBuffer.wrap(transaction.getSignature()));
                }
            }
        }
    }
}
