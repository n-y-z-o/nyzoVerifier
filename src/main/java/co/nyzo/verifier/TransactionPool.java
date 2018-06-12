package co.nyzo.verifier;

import co.nyzo.verifier.messages.TransactionPoolResponse;
import co.nyzo.verifier.util.IpUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class TransactionPool {

    private static long minimumAcceptedHeight = 1L;
    private static final Map<ByteBuffer, Transaction> transactionPool = new HashMap<>();

    public static synchronized void addTransaction(Transaction transaction) {

        if (BlockManager.heightForTimestamp(transaction.getTimestamp()) >= minimumAcceptedHeight) {
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

    public static synchronized void removeTransactionsToHeight(long blockHeight) {

        if (blockHeight > minimumAcceptedHeight) {
            minimumAcceptedHeight = blockHeight;
            long cutoffTimestamp = BlockManager.endTimestampForHeight(blockHeight);
            List<Transaction> transactions = allTransactions();
            for (Transaction transaction : transactions) {
                if (transaction.getTimestamp() < cutoffTimestamp) {
                    transactionPool.remove(ByteBuffer.wrap(transaction.getSignature()));
                }
            }
        }
    }

    public static void fetchFromMesh() {

        List<Node> availableNodes = new ArrayList<>(NodeManager.getMesh());
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
}
