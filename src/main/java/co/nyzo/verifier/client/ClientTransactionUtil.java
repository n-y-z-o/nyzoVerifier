package co.nyzo.verifier.client;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.TransactionResponse;
import co.nyzo.verifier.nyzoString.NyzoStringPrivateSeed;
import co.nyzo.verifier.nyzoString.NyzoStringPublicIdentifier;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.ThreadUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientTransactionUtil {

    public static void createAndSendTransaction(NyzoStringPrivateSeed signerSeed,
                                                NyzoStringPublicIdentifier receiverIdentifier, byte[] senderData,
                                                long amount) {
        createAndSendTransaction(signerSeed, receiverIdentifier, senderData, amount, null, 0);
    }

    public static void createAndSendTransaction(NyzoStringPrivateSeed signerSeed,
                                                NyzoStringPublicIdentifier receiverIdentifier, byte[] senderData,
                                                long amount, byte[] receiverIpAddress, int receiverPort) {

        // Check if the blockchain is behind. If so, a timestamp offset can be applied to prevent transaction delay.
        Block localFrozenEdge = BlockManager.getFrozenEdge();
        long timeSinceFrozenEdgeVerification = System.currentTimeMillis() - localFrozenEdge.getVerificationTimestamp();
        long predictedConsensusFrozenEdge = localFrozenEdge.getBlockHeight() + timeSinceFrozenEdgeVerification /
                Block.blockDuration;

        // The offset is calculated based on the difference between the open edge and predicted consensus edge, with a
        // small buffer (3 blocks).
        long timestampOffset = Math.max(0L, (BlockManager.openEdgeHeight(false) - predictedConsensusFrozenEdge - 3) *
                Block.blockDuration);

        // Make the transaction.
        long timestamp = System.currentTimeMillis() - timestampOffset;
        Transaction transaction = Transaction.standardTransaction(timestamp, amount, receiverIdentifier.getIdentifier(),
                localFrozenEdge.getBlockHeight(), localFrozenEdge.getHash(), senderData, signerSeed.getSeed());

        System.out.println("open edge: " + BlockManager.openEdgeHeight(false) + ", predicted consensus edge: " +
                predictedConsensusFrozenEdge + ", offset: " + timestampOffset);

        if (receiverIpAddress == null || ByteUtil.isAllZeros(receiverIpAddress) || receiverPort <= 0) {
            sendTransactionToCycle(transaction);
        } else {
            sendTransactionToReceiver(transaction, receiverIpAddress, receiverPort);
        }
    }

    private static void sendTransactionToReceiver(Transaction transaction, byte[] ipAddressBytes, int port) {

        // Attempt to send the transaction to the receiver up to 3 times, stopping when a transaction response is
        // received.
        AtomicBoolean transactionAccepted = new AtomicBoolean(false);
        String ipAddress = IpUtil.addressAsString(ipAddressBytes);
        for (int i = 0; i < 3 && !transactionAccepted.get(); i++) {
            Message message = new Message(MessageType.Transaction5, transaction);
            Message.fetchTcp(ipAddress, port, message, new MessageCallback() {
                @Override
                public void responseReceived(Message message) {
                    if (message != null && (message.getContent() instanceof TransactionResponse)) {
                        TransactionResponse response = (TransactionResponse) message.getContent();
                        if (response.isAccepted()) {
                            System.out.println("transaction accepted by " +
                                    NicknameManager.get(message.getSourceNodeIdentifier()));
                            transactionAccepted.set(true);
                        } else {
                            System.out.println(ConsoleColor.Red + "transaction not accepted by " +
                                    NicknameManager.get(message.getSourceNodeIdentifier()) +
                                    ConsoleColor.reset);
                        }
                    }
                }
            });

            // Wait up to 2 seconds for the next iteration.
            for (int j = 0; j < 10 && !transactionAccepted.get(); j++) {
                ThreadUtil.sleep(200L);
            }
        }

        // If the transaction was not accepted, print an error.
        if (!transactionAccepted.get()) {
            System.out.println(ConsoleColor.Red + "unable to send transaction to " + ipAddress + ConsoleColor.reset);
        }
    }

    private static void sendTransactionToCycle(Transaction transaction) {

        // Build a lookup for the nodes for each verifier in the cycle.
        Map<ByteBuffer, List<Node>> identifierToNodeListMap = new HashMap<>();
        for (Node node : ClientNodeManager.getMesh()) {
            ByteBuffer identifier = ByteBuffer.wrap(node.getIdentifier());
            List<Node> nodesForIdentifier = identifierToNodeListMap.get(identifier);
            if (nodesForIdentifier == null) {
                nodesForIdentifier = new ArrayList<>();
                identifierToNodeListMap.put(identifier, nodesForIdentifier);
            }

            nodesForIdentifier.add(node);
        }

        // Send the transaction to the mesh. This is done in two phases. First, the transaction is sent quickly to
        // a number of verifiers based on the probable number of blocks that have been processed since the local
        // frozen edge (limited to 10 to avoid excess traffic). Then, it will be sent slowly to other verifiers
        // until the entire cycle has received it or until the block in which it should have been incorporated is
        // frozen.
        int numberOfQuickSends = (int) Math.min(((System.currentTimeMillis() -
                BlockManager.getFrozenEdge().getVerificationTimestamp()) / Block.blockDuration), 10);
        System.out.println("number of quick sends: " + numberOfQuickSends);
        List<ByteBuffer> currentCycle = BlockManager.verifiersInCurrentCycleList();
        long transactionHeight = BlockManager.heightForTimestamp(transaction.getTimestamp());
        for (int i = 0; i < currentCycle.size() && BlockManager.getFrozenEdgeHeight() < transactionHeight; i++) {

            List<Node> nodesForIdentifier = identifierToNodeListMap.get(currentCycle.get(i));
            if (nodesForIdentifier == null) {
                System.out.println(ConsoleColor.Yellow + "unable to find node for " +
                        NicknameManager.get(currentCycle.get(i).array()) + ConsoleColor.reset);
            } else {
                for (Node node : nodesForIdentifier) {
                    Message message = new Message(MessageType.Transaction5, transaction);
                    Message.fetch(node, message, new MessageCallback() {
                        @Override
                        public void responseReceived(Message message) {

                            // Print the transaction response.
                            if (message == null) {
                                System.out.println(ConsoleColor.Red + "transaction response: null" +
                                        ConsoleColor.reset);
                            } else {
                                if (message.getContent() instanceof TransactionResponse) {
                                    TransactionResponse response = (TransactionResponse) message.getContent();
                                    if (response.isAccepted()) {
                                        System.out.println("transaction accepted by " +
                                                NicknameManager.get(message.getSourceNodeIdentifier()));
                                    } else {
                                        System.out.println(ConsoleColor.Yellow + "transaction not accepted by " +
                                                NicknameManager.get(message.getSourceNodeIdentifier()) +
                                                ConsoleColor.reset);
                                    }
                                } else {
                                    System.out.println(ConsoleColor.Red + "transaction response: invalid" +
                                            ConsoleColor.reset);
                                }
                            }
                        }
                    });
                }
            }

            // If still in the quick-send range, sleep 0.1 seconds. Otherwise, sleep 3 seconds.
            ThreadUtil.sleep(i < numberOfQuickSends ? 100 : 3000);
        }

        // Now, get the block in which the transaction was supposed to be incorporated. Report whether the
        // transaction is in the block.
        Block transactionBlock = BlockManager.frozenBlockForHeight(transactionHeight);
        if (transactionBlock == null) {
            System.out.println(ConsoleColor.Red + "unable to determine whether transaction was incorporated into " +
                    "the chain" + ConsoleColor.reset);
        } else {
            boolean transactionIsInChain = false;
            for (Transaction blockTransaction : transactionBlock.getTransactions()) {
                if (ByteUtil.arraysAreEqual(blockTransaction.getSignature(), transaction.getSignature())) {
                    transactionIsInChain = true;
                }
            }

            if (transactionIsInChain) {
                System.out.println(ConsoleColor.Green + "transaction processed in block " +
                        transactionBlock.getBlockHeight() + " with transaction signature " +
                        PrintUtil.compactPrintByteArray(transaction.getSignature()) + ConsoleColor.reset);
            } else {
                System.out.println(ConsoleColor.Red + "transaction not processed" + ConsoleColor.reset);
            }
        }
    }

    public static String senderDataString(byte[] senderData) {

        // Sender data is stored and handled as a raw array of bytes. Often, this byte array represents a character
        // string. If encoding to a UTF-8 character string and back to a byte array produces the original byte array,
        // display as a string. Otherwise, display the hex values of the bytes.
        String result = new String(senderData, StandardCharsets.UTF_8);
        if (!ByteUtil.arraysAreEqual(senderData, result.getBytes(StandardCharsets.UTF_8))) {
            result = ByteUtil.arrayAsStringWithDashes(senderData);
        }

        return result;
    }
}
