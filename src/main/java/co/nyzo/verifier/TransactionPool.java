package co.nyzo.verifier;

import co.nyzo.verifier.messages.TransactionPoolResponse;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class TransactionPool {

    private static long minimumAcceptedHeight = 1L;
    private static final List<Transaction> transactionPool = new ArrayList<>();

    public static synchronized void addTransaction(Transaction transaction) {
        if (BlockManager.heightForTimestamp(transaction.getTimestamp()) >= minimumAcceptedHeight) {
            transactionPool.add(transaction);
        }
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

        if (blockHeight > minimumAcceptedHeight) {
            minimumAcceptedHeight = blockHeight;
            long cutoffTimestamp = BlockManager.endTimestampForHeight(blockHeight);
            for (int i = transactionPool.size() - 1; i >= 0; i--) {
                if (transactionPool.get(i).getTimestamp() < cutoffTimestamp) {
                    transactionPool.remove(i);
                }
            }
        }
    }

    public static void fetchFromMesh() {

        List<Node> availableNodes = new ArrayList<>(NodeManager.getNodePool());
        Random random = new Random();
        for (int i = 0; i < 5 && availableNodes.size() > 0; i++) {
            Node node = availableNodes.remove(random.nextInt(availableNodes.size()));

            String ipAddress = IpUtil.addressAsString(node.getIpAddress());
            Message.fetch(ipAddress, node.getPort(), new Message(MessageType.TransactionPoolRequest13, null), false,
                    new MessageCallback() {
                        @Override
                        public void responseReceived(Message message) {
                            TransactionPoolResponse response = (TransactionPoolResponse) message.getContent();
                            if (response != null) {
                                for (Transaction transaction : response.getTransactions()) {
                                    addTransaction(transaction);
                                }
                            }
                        }
                    });
        }
    }

    public static void reset() {


    }
}
