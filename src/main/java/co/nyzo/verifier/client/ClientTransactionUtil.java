package co.nyzo.verifier.client;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.CycleTransactionSignature;
import co.nyzo.verifier.messages.CycleTransactionSignatureResponse;
import co.nyzo.verifier.messages.TransactionResponse;
import co.nyzo.verifier.nyzoString.*;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.ThreadUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientTransactionUtil {

    public static long suggestedTransactionTimestamp() {

        // Check if the blockchain is behind. If so, a timestamp offset can be applied to prevent transaction delay.
        Block localFrozenEdge = BlockManager.getFrozenEdge();
        long timeSinceFrozenEdgeVerification = System.currentTimeMillis() - localFrozenEdge.getVerificationTimestamp();
        long predictedConsensusFrozenEdge = localFrozenEdge.getBlockHeight() + timeSinceFrozenEdgeVerification /
                Block.blockDuration;

        // The offset is calculated based on the difference between the open edge and predicted consensus edge, with a
        // small buffer (3 blocks).
        long timestampOffset = Math.max(0L, (BlockManager.openEdgeHeight(false) - predictedConsensusFrozenEdge - 3) *
                Block.blockDuration);

        System.out.println("open edge: " + BlockManager.openEdgeHeight(false) + ", predicted consensus edge: " +
                predictedConsensusFrozenEdge + ", offset: " + timestampOffset);

        // Return the timestamp.
        return System.currentTimeMillis() - timestampOffset;
    }

    private static Transaction createTransaction(NyzoStringPrivateSeed signerSeed,
                                                NyzoStringPublicIdentifier receiverIdentifier, byte[] senderData,
                                                long amount) {
        long timestamp = suggestedTransactionTimestamp();
        Block localFrozenEdge = BlockManager.getFrozenEdge();
        return Transaction.standardTransaction(timestamp, amount, receiverIdentifier.getIdentifier(),
                localFrozenEdge.getBlockHeight(), localFrozenEdge.getHash(), senderData, signerSeed.getSeed());
    }

    public static void createAndSendTransaction(NyzoStringPrivateSeed signerSeed,
                                                NyzoStringPublicIdentifier receiverIdentifier, byte[] senderData,
                                                long amount) {
        createAndSendTransaction(signerSeed, receiverIdentifier, senderData, amount, null, 0);
    }

    public static void createAndSendTransaction(NyzoStringPrivateSeed signerSeed,
                                                NyzoStringPublicIdentifier receiverIdentifier, byte[] senderData,
                                                long amount, byte[] receiverIpAddress, int receiverPort) {

        Transaction transaction = createTransaction(signerSeed, receiverIdentifier, senderData, amount);

        if (receiverIpAddress == null || ByteUtil.isAllZeros(receiverIpAddress) || receiverPort <= 0) {
            sendTransactionToLikelyBlockVerifiers(transaction, true);
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

    public static void sendTransactionToLikelyBlockVerifiers(Transaction transaction, boolean waitForBlock) {

        // Determine the height at which the transaction will be included.
        long transactionHeight = BlockManager.heightForTimestamp(transaction.getTimestamp());

        // Get the current frozen edge and the current cycle. Using the frozen edge as a reference, the verifier that
        // should be expected to verify this block can be determined based on its position in the cycle.
        Block frozenEdge = BlockManager.getFrozenEdge();
        List<ByteBuffer> currentCycle = BlockManager.verifiersInCurrentCycleList();
        int frozenEdgeVerifierIndex = currentCycle.indexOf(ByteBuffer.wrap(frozenEdge.getVerifierIdentifier()));

        // Send the transaction to the expected verifier, the previous verifier, and the next verifier.
        Set<ByteBuffer> likelyVerifiers = new HashSet<>();
        for (int i = -1; i < 2; i++) {
            int indexOfVerifier = (int) ((transactionHeight - frozenEdge.getBlockHeight() +
                    frozenEdgeVerifierIndex + i) % currentCycle.size());
            if (indexOfVerifier >= 0 && indexOfVerifier < currentCycle.size()) {
                likelyVerifiers.add(ByteBuffer.wrap(currentCycle.get(indexOfVerifier).array()));
            }
        }

        for (Node node : ClientNodeManager.getMesh()) {
            if (likelyVerifiers.contains(ByteBuffer.wrap(node.getIdentifier()))) {
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
                                            NicknameManager.get(message.getSourceNodeIdentifier()) + ": " +
                                            response.getMessage() + ConsoleColor.reset);
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

        // If indicated, wait for the block that should incorporate the transaction to be received.
        if (waitForBlock) {

            // Wait for the transaction's block to be received.
            while (BlockManager.getFrozenEdgeHeight() < transactionHeight) {
                ThreadUtil.sleep(1000L);
            }
            ThreadUtil.sleep(200L);

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
    }

    public static void sendCycleTransaction(Transaction transaction) {

        // Cycle transactions are sent to all verifiers in the cycle, retrying once for failures.
        Set<Node> nodesReceived = ConcurrentHashMap.newKeySet();
        Set<ByteBuffer> cycleVerifiers = BlockManager.verifiersInCurrentCycleSet();
        for (int i = 0; i < 2; i++) {
            for (Node node : ClientNodeManager.getMesh()) {
                if (!nodesReceived.contains(node) && cycleVerifiers.contains(ByteBuffer.wrap(node.getIdentifier()))) {
                    if (i == 0) {
                        System.out.println("sending transaction to " + NicknameManager.get(node.getIdentifier()));
                    } else {
                        System.out.println("resending transaction to " + NicknameManager.get(node.getIdentifier()));
                    }
                    Message message = new Message(MessageType.Transaction5, transaction);
                    Message.fetch(node, message, new MessageCallback() {
                        @Override
                        public void responseReceived(Message message) {
                            System.out.println("response: " + message);
                            if (message != null && message.getType() == MessageType.TransactionResponse6) {
                                nodesReceived.add(node);
                                if (message.getContent() instanceof TransactionResponse) {
                                    TransactionResponse response = (TransactionResponse) message.getContent();
                                    System.out.println("response: " + response);
                                }
                            }
                        }
                    });
                    ThreadUtil.sleep(500L);  // sleep 0.5 seconds after each send to limit traffic to a reasonable rate
                }
            }
        }
    }

    public static void sendCycleTransactionSignature(CycleTransactionSignature signature) {

        // Cycle transaction signatures are sent to all verifiers in the cycle, retrying once for failures.
        Set<Node> nodesReceived = ConcurrentHashMap.newKeySet();
        Set<ByteBuffer> cycleVerifiers = BlockManager.verifiersInCurrentCycleSet();
        for (int i = 0; i < 2; i++) {
            for (Node node : ClientNodeManager.getMesh()) {
                if (!nodesReceived.contains(node) && cycleVerifiers.contains(ByteBuffer.wrap(node.getIdentifier()))) {
                    if (i == 0) {
                        System.out.println("sending signature to " + NicknameManager.get(node.getIdentifier()));
                    } else {
                        System.out.println("resending signature to " + NicknameManager.get(node.getIdentifier()));
                    }
                    Message message = new Message(MessageType.CycleTransactionSignature47, signature);
                    Message.fetch(node, message, new MessageCallback() {
                        @Override
                        public void responseReceived(Message message) {
                            System.out.println("response: " + message);
                            if (message != null &&
                                    message.getType() == MessageType.CycleTransactionSignatureResponse48) {
                                nodesReceived.add(node);
                                if (message.getContent() instanceof CycleTransactionSignatureResponse) {
                                    TransactionResponse response = (TransactionResponse) message.getContent();
                                    System.out.println("response: " + response);
                                }
                            }
                        }
                    });
                    ThreadUtil.sleep(500L);  // sleep 0.5 seconds after each send to limit traffic to a reasonable rate
                }
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
