package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.*;
import co.nyzo.verifier.messages.TransactionListResponse;
import co.nyzo.verifier.nyzoString.*;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.ThreadUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CycleTransactionListCommand implements Command {

    @Override
    public String getShortCommand() {
        return "CTL";
    }

    @Override
    public String getLongCommand() {
        return "cycleList";
    }

    @Override
    public String getDescription() {
        return "list cycle transactions";
    }

    @Override
    public String[] getArgumentNames() {
        return new String[] { "in-cycle verifier key (leave empty to list locally stored transactions)" };
    }

    @Override
    public boolean requiresValidation() {
        return true;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ValidationResult validate(List<String> argumentValues) {

        ValidationResult result = null;
        try {
            // Make a list for the argument result items.
            List<ArgumentResult> argumentResults = new ArrayList<>();

            // Check the verifier key.
            NyzoString verifierKey = NyzoStringEncoder.decode(argumentValues.get(0));
            if (verifierKey instanceof NyzoStringPrivateSeed) {
                argumentResults.add(new ArgumentResult(true, NyzoStringEncoder.encode(verifierKey)));
            } else if (argumentValues.get(0).trim().isEmpty()) {
                argumentResults.add(new ArgumentResult(true, ""));
            } else {
                String message = "not a valid Nyzo string private key";
                argumentResults.add(new ArgumentResult(false, argumentValues.get(0), message));

                if (argumentValues.get(0).length() >= 64) {
                    PrivateNyzoStringCommand.printHexWarning();
                }
            }

            // Produce the result.
            result = new ValidationResult(argumentResults);

        } catch (Exception ignored) { }

        // If the confirmation result is null, create an exception result. This will only happen if an exception is not
        // handled properly by the validation code.
        if (result == null) {
            result = ValidationResult.exceptionResult(getArgumentNames().length);
        }

        return result;
    }

    @Override
    public void run(List<String> argumentValues) {

        try {
            // If a key was provided, query the corresponding verifier.
            if (!argumentValues.get(0).isEmpty()) {
                NyzoStringPrivateSeed verifierKey =
                        (NyzoStringPrivateSeed) NyzoStringEncoder.decode(argumentValues.get(0));
                byte[] identifier = KeyUtil.identifierForSeed(verifierKey.getSeed());

                // Print a warning if the verifier does not appear to be in the cycle.
                if (!BlockManager.verifierInCurrentCycle(ByteBuffer.wrap(identifier))) {
                    System.out.println(ConsoleColor.Yellow.background() + "warning: the verifier you specified does " +
                            "not appear to be in the cycle" + ConsoleColor.reset);
                }

                // Query each verifier that matches the specified key. This should be an in-cycle verifier to work
                // properly, but the verifier will respond as long as the message is signed by the correct key.
                int numberQueried = 0;
                AtomicInteger numberWaiting = new AtomicInteger(0);
                for (Node node : ClientNodeManager.getMesh()) {
                    if (ByteUtil.arraysAreEqual(node.getIdentifier(), identifier)) {
                        System.out.println("querying node " + NicknameManager.get(identifier));
                        numberQueried++;
                        numberWaiting.incrementAndGet();
                        Message message = new Message(MessageType.CycleTransactionListRequest49, null);
                        message.sign(verifierKey.getSeed());
                        Message.fetchTcp(IpUtil.addressAsString(node.getIpAddress()), MeshListener.standardPortTcp,
                                message, new MessageCallback() {
                                    @Override
                                    public void responseReceived(Message message) {

                                        processResponse(message);
                                        numberWaiting.decrementAndGet();
                                    }
                                });
                    }
                }

                // Wait for all responses to be processed.
                while (numberWaiting.get() > 0) {
                    ThreadUtil.sleep(300L);
                }
            }

            // Perform maintenance on the transaction manager. This removes old transactions.
            CycleTransactionManager.performMaintenance();

            // Show the transactions.
            List<Transaction> transactions = getTransactionList();
            if (transactions.isEmpty()) {
                ConsoleUtil.printTable("no cycle transactions available");
            } else {
                List<String> indexColumn = new ArrayList<>(Collections.singletonList("index"));
                List<String> initiatorColumn = new ArrayList<>(Collections.singletonList("initiator"));
                List<String> receiverColumn = new ArrayList<>(Collections.singletonList("receiver"));
                List<String> amountColumn = new ArrayList<>(Collections.singletonList("amount"));
                List<String> blockColumn = new ArrayList<>(Collections.singletonList("block"));
                List<String> numberOfSignaturesColumn = new ArrayList<>(Collections.singletonList("# signatures"));
                for (int i = 0; i < transactions.size(); i++) {
                    indexColumn.add(i + "");

                    Transaction transaction = transactions.get(i);
                    NyzoStringPublicIdentifier initiator =
                            new NyzoStringPublicIdentifier(transaction.getSenderIdentifier());
                    initiatorColumn.add(NyzoStringEncoder.encode(initiator));

                    NyzoStringPublicIdentifier receiver =
                            new NyzoStringPublicIdentifier(transaction.getReceiverIdentifier());
                    receiverColumn.add(NyzoStringEncoder.encode(receiver));

                    amountColumn.add(PrintUtil.printAmount(transaction.getAmount()));
                    blockColumn.add(BlockManager.heightForTimestamp(transaction.getTimestamp()) + "");
                    numberOfSignaturesColumn.add((transaction.getCycleSignatures().size() + 1) + "");
                }
                ConsoleUtil.printTable(Arrays.asList(indexColumn, initiatorColumn, receiverColumn, amountColumn,
                        blockColumn, numberOfSignaturesColumn), new HashSet<>(Collections.singletonList(0)));
            }

        } catch (Exception e) {
            System.out.println(ConsoleColor.Red + "unexpected issue list cycle transactions: " +
                    PrintUtil.printException(e) + ConsoleColor.reset);
        }
    }

    private static void processResponse(Message message) {

        if (message != null && (message.getContent() instanceof TransactionListResponse)) {
            TransactionListResponse response = (TransactionListResponse) message.getContent();
            for (Transaction transaction : response.getTransactions()) {
                CycleTransactionManager.registerTransaction(transaction, null, null);
            }
        }
    }

    public static List<Transaction> getTransactionList() {

        // Impose a standard ordering on transactions.
        List<Transaction> transactions = new ArrayList<>(CycleTransactionManager.getTransactions());
        transactions.sort(new Comparator<Transaction>() {
            @Override
            public int compare(Transaction transaction1, Transaction transaction2) {
                // Primary ordering is on timestamp, secondary ordering is on initiator identifier string.
                if (transaction1.getTimestamp() != transaction2.getTimestamp()) {
                    return Long.compare(transaction1.getTimestamp(), transaction2.getTimestamp());
                } else {
                    NyzoStringPublicIdentifier initiator1 =
                            new NyzoStringPublicIdentifier(transaction1.getSenderIdentifier());
                    NyzoStringPublicIdentifier initiator2 =
                            new NyzoStringPublicIdentifier(transaction2.getSenderIdentifier());
                    return NyzoStringEncoder.encode(initiator1).compareTo(NyzoStringEncoder.encode(initiator2));
                }
            }
        });

        return transactions;
    }
}
