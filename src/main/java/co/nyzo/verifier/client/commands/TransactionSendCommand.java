package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.*;
import co.nyzo.verifier.messages.TransactionResponse;
import co.nyzo.verifier.nyzoString.NyzoString;
import co.nyzo.verifier.nyzoString.NyzoStringEncoder;
import co.nyzo.verifier.nyzoString.NyzoStringPrivateSeed;
import co.nyzo.verifier.nyzoString.NyzoStringPublicIdentifier;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.ThreadUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TransactionSendCommand implements Command {

    @Override
    public String getShortCommand() {
        return "ST";
    }

    @Override
    public String getLongCommand() {
        return "send";
    }

    @Override
    public String getDescription() {
        return "send a transaction";
    }

    @Override
    public String[] getArgumentNames() {
        return new String[] { "sender key", "receiver ID", "sender data", "amount, Nyzos" };
    }

    @Override
    public boolean requiresValidation() {
        return true;
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public ValidationResult validate(List<String> argumentValues) {

        ValidationResult result = null;
        try {
            // Make a list for the argument result items.
            List<ArgumentResult> argumentResults = new ArrayList<>();

            // Check the sender key.
            NyzoString senderKey = NyzoStringEncoder.decode(argumentValues.get(0));
            if (senderKey instanceof NyzoStringPrivateSeed) {
                argumentResults.add(new ArgumentResult(true, NyzoStringEncoder.encode(senderKey)));
            } else {
                String message = argumentValues.get(0).trim().isEmpty() ? "missing Nyzo string private key" :
                        "not a valid Nyzo string private key";
                argumentResults.add(new ArgumentResult(false, argumentValues.get(0), message));
            }

            // Check the receiver ID.
            NyzoString receiverIdentifier = NyzoStringEncoder.decode(argumentValues.get(1));
            if (receiverIdentifier instanceof NyzoStringPublicIdentifier) {
                boolean receiverIsValid = true;
                String receiverError = "";
                if (senderKey instanceof NyzoStringPrivateSeed) {
                    byte[] senderId = KeyUtil.identifierForSeed(((NyzoStringPrivateSeed) senderKey).getSeed());
                    if (ByteUtil.arraysAreEqual(senderId,
                            ((NyzoStringPublicIdentifier) receiverIdentifier).getIdentifier())) {
                        receiverIsValid = false;
                        receiverError = "sender and receiver are same";
                    }
                }

                argumentResults.add(new ArgumentResult(receiverIsValid, NyzoStringEncoder.encode(receiverIdentifier),
                        receiverError));
            } else {
                String message = argumentValues.get(1).trim().isEmpty() ? "missing Nyzo string public ID" :
                        "not a valid Nyzo string public ID";
                argumentResults.add(new ArgumentResult(false, argumentValues.get(1), message));
            }

            // Process the sender data.
            byte[] senderDataBytes = argumentValues.get(2).getBytes(StandardCharsets.UTF_8);
            if (senderDataBytes.length > FieldByteSize.maximumSenderDataLength) {
                senderDataBytes = Arrays.copyOf(senderDataBytes, FieldByteSize.maximumSenderDataLength);
            }
            String senderData = new String(senderDataBytes, StandardCharsets.UTF_8);
            argumentResults.add(new ArgumentResult(true, senderData, ""));

            // Check the amount.
            long amountMicronyzos = -1L;
            try {
                amountMicronyzos = (long) (Double.parseDouble(argumentValues.get(3)) *
                        Transaction.micronyzoMultiplierRatio);
            } catch (Exception ignored) { }
            if (amountMicronyzos > 0) {
                double amountNyzos = amountMicronyzos / (double) Transaction.micronyzoMultiplierRatio;
                argumentResults.add(new ArgumentResult(true, String.format("%.6f", amountNyzos), ""));
            } else {
                argumentResults.add(new ArgumentResult(false, argumentValues.get(3), "invalid amount"));
            }

            // Produce the result.
            result = new ValidationResult(argumentResults);

        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        // If the confirmation result is null, create an error result. This will only happen if an exception is not
        // handled properly by the confirmation code.
        if (result == null) {
            result = new ValidationResult(null);
        }

        return result;
    }

    @Override
    public void run(List<String> argumentValues) {

        System.out.println("have complete cycle: " + BlockManager.isCycleComplete());

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
        Transaction transaction = null;
        try {
            long timestamp = System.currentTimeMillis() - timestampOffset;
            NyzoStringPrivateSeed signerSeed = (NyzoStringPrivateSeed) NyzoStringEncoder.decode(argumentValues.get(0));
            NyzoStringPublicIdentifier receiverIdentifier =
                    (NyzoStringPublicIdentifier) NyzoStringEncoder.decode(argumentValues.get(1));
            byte[] senderData = argumentValues.get(2).getBytes(StandardCharsets.UTF_8);
            long amount = (long) (Double.parseDouble(argumentValues.get(3)) * Transaction.micronyzoMultiplierRatio);
            transaction = Transaction.standardTransaction(timestamp, amount, receiverIdentifier.getIdentifier(),
                    localFrozenEdge.getBlockHeight(), localFrozenEdge.getHash(), senderData, signerSeed.getSeed());
        } catch (Exception e) {
            System.out.println(ConsoleColor.Red + "an unexpected error occurred in transaction creation: " +
                    PrintUtil.printException(e) + ConsoleColor.reset);
        }

        System.out.println("open edge: " + BlockManager.openEdgeHeight(false) + ", predicted consensus edge: " +
                predictedConsensusFrozenEdge + ", offset: " + timestampOffset);

        if (transaction != null) {
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
                    localFrozenEdge.getVerificationTimestamp()) / Block.blockDuration), 10);
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
    }
}
