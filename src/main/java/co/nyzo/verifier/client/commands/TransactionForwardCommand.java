package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.*;
import co.nyzo.verifier.nyzoString.*;
import co.nyzo.verifier.util.PreferencesUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TransactionForwardCommand implements Command {

    private static final AtomicInteger requestsSinceMaintenance = new AtomicInteger(0);
    private static final int maintenanceInterval = 10;
    private static final Map<String, Transaction> recentlyForwardedTransactions = new ConcurrentHashMap<>();
    private static final int maximumMapSize = PreferencesUtil.getInt("transaction_forward_command_maximum_map_size",
            1000);

    @Override
    public String getShortCommand() {
        return "TF";
    }

    @Override
    public String getLongCommand() {
        return "forwardTransaction";
    }

    @Override
    public String getDescription() {
        return "forward a transaction";
    }

    @Override
    public String[] getArgumentNames() {
        return new String[] { "transaction (Nyzo string)", "supplemental transaction (Nyzo string, optional)" };
    }

    @Override
    public String[] getArgumentIdentifiers() {
        return new String[] { "transaction", "supplementalTransaction" };
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
    public boolean isLongRunning() {
        return false;
    }

    @Override
    public ValidationResult validate(List<String> argumentValues, CommandOutput output) {

        ValidationResult result = null;
        try {
            // Make a list for the argument result items.
            List<ArgumentResult> argumentResults = new ArrayList<>();

            // Check the transaction.
            NyzoString transaction = NyzoStringEncoder.decode(argumentValues.get(0));
            if (transaction instanceof NyzoStringTransaction) {
                argumentResults.add(new ArgumentResult(true, NyzoStringEncoder.encode(transaction)));
            } else {
                String message = argumentValues.get(0).trim().isEmpty() ? "missing Nyzo string transaction" :
                        "not a valid Nyzo string transaction";
                argumentResults.add(new ArgumentResult(false, argumentValues.get(0), message));
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
    public ExecutionResult run(List<String> argumentValues, CommandOutput output) {

        // Periodically perform maintenance on the recent-transactions map.
        if (requestsSinceMaintenance.incrementAndGet() >= maintenanceInterval) {
            performMaintenance();
        }

        // Make the lists for the notices and errors. Make the result table.
        List<String> notices = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        CommandTable table = new CommandTable(new CommandTableHeader("block height", "blockHeight"),
                new CommandTableHeader("sender ID (raw)", "senderIdBytes", true),
                new CommandTableHeader("sender ID (Nyzo string)", "senderIdNyzoString", true),
                new CommandTableHeader("receiver ID (raw)", "receiverIdBytes", true),
                new CommandTableHeader("receiver ID (Nyzo string)", "receiverIdNyzoString", true),
                new CommandTableHeader("amount", "amount"),
                new CommandTableHeader("previous verifier ID (raw)", "previousVerifierIdBytes", true),
                new CommandTableHeader("previous verifier ID (Nyzo string)", "previousVerifierIdNyzoString", true),
                new CommandTableHeader("expected verifier ID (raw)", "expectedVerifierIdBytes", true),
                new CommandTableHeader("expected verifier ID (Nyzo string)", "expectedVerifierIdNyzoString", true),
                new CommandTableHeader("next verifier ID (raw)", "nextVerifierIdBytes", true),
                new CommandTableHeader("next verifier ID (Nyzo string)", "nextVerifierIdNyzoString", true),
                new CommandTableHeader("forwarded", "forwarded"),
                new CommandTableHeader("previously forwarded", "previouslyForwarded"),
                new CommandTableHeader("in blockchain", "inBlockchain"),
                new CommandTableHeader("age", "age"),
                new CommandTableHeader("sender balance", "senderBalance"),
                new CommandTableHeader("supplemental transaction valid", "supplementalTransactionValid"),
                new CommandTableHeader("sender data (raw)", "senderDataBytes"));

        try {
            // Get the transaction string from the arguments.
            String transactionString = argumentValues.get(0);

            // Get the transaction from the transaction string.
            NyzoString transactionObject = NyzoStringEncoder.decode(transactionString);
            if (transactionObject instanceof NyzoStringTransaction) {
                Transaction transaction = ((NyzoStringTransaction) transactionObject).getTransaction();

                // If the transaction is under the frozen edge, remove it from the recently forwarded map. This cleans
                // the map, and it also avoids providing an indication that a transaction has a chance of being
                // processed when that chance has passed.
                long transactionHeight = BlockManager.heightForTimestamp(transaction.getTimestamp());
                if (transactionHeight <= BlockManager.getFrozenEdgeHeight()) {
                    recentlyForwardedTransactions.remove(transactionString);
                }

                // Check if the transaction was already forwarded.
                boolean previouslyForwarded = recentlyForwardedTransactions.containsKey(transactionString);

                // Check if the transaction is already in the blockchain.
                boolean inBlockchain = false;
                if (!previouslyForwarded && transactionHeight <= BlockManager.getFrozenEdgeHeight()) {
                    Block block = BlockManager.frozenBlockForHeight(transactionHeight);
                    if (block == null) {
                        block = HistoricalBlockManager.blockForHeight(transactionHeight);
                    }
                    if (block != null) {
                        for (Transaction blockTransaction : block.getTransactions()) {
                            if (ByteUtil.arraysAreEqual(transaction.getSignature(), blockTransaction.getSignature())) {
                                inBlockchain = true;
                            }
                        }
                    }
                }

                // Perform initial validation on the transaction.
                boolean valid = false;
                if (!previouslyForwarded && !inBlockchain) {
                    StringBuilder validationError = new StringBuilder();
                    StringBuilder validationWarning = new StringBuilder();
                    valid = transaction.performInitialValidation(validationError, validationWarning);
                    if (validationError.length() > 0) {
                        errors.add(validationError.toString().trim());
                    }
                    if (validationWarning.length() > 0) {
                        notices.add(validationWarning.toString().trim());
                    }
                }

                // If valid, send the transaction to the cycle.
                ByteBuffer[] verifiers;
                if (valid) {
                    verifiers = ClientTransactionUtil.sendTransactionToLikelyBlockVerifiers(transaction, false, output);
                    recentlyForwardedTransactions.put(transactionString, transaction);
                } else {
                    verifiers = new ByteBuffer[3];
                }

                // Check if the supplemental transaction is valid.
                boolean supplementalTransactionValid = false;
                String supplementalTransactionString = argumentValues.get(1);
                NyzoString supplementalTransactionObject = NyzoStringEncoder.decode(supplementalTransactionString);
                if (supplementalTransactionObject instanceof NyzoStringTransaction) {
                    Transaction supplementalTransaction =
                            ((NyzoStringTransaction) supplementalTransactionObject).getTransaction();
                    supplementalTransactionValid = supplementalTransaction.signatureIsValid() &&
                            ByteUtil.arraysAreEqual(transaction.getSenderIdentifier(),
                                    supplementalTransaction.getSenderIdentifier());
                }

                // Build the result.
                table.addRow(transactionHeight,
                        ByteUtil.arrayAsStringWithDashes(transaction.getSenderIdentifier()),
                        NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(transaction.getSenderIdentifier())),
                        transaction.getReceiverIdentifier() == null ? null :
                                ByteUtil.arrayAsStringWithDashes(transaction.getReceiverIdentifier()),
                        transaction.getReceiverIdentifier() == null ? null : NyzoStringEncoder.encode(
                                new NyzoStringPublicIdentifier(transaction.getReceiverIdentifier())),
                        PrintUtil.printAmount(transaction.getAmount()),
                        verifiers[0] == null ? null : ByteUtil.arrayAsStringWithDashes(verifiers[0].array()),
                        verifiers[0] == null ? null :
                                NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(verifiers[0].array())),
                        verifiers[1] == null ? null : ByteUtil.arrayAsStringWithDashes(verifiers[1].array()),
                        verifiers[1] == null ? null :
                                NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(verifiers[1].array())),
                        verifiers[2] == null ? null : ByteUtil.arrayAsStringWithDashes(verifiers[2].array()),
                        verifiers[2] == null ? null :
                                NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(verifiers[2].array())),
                        valid, previouslyForwarded, inBlockchain,
                        (System.currentTimeMillis() - transaction.getTimestamp()) / 1000.0,
                        PrintUtil.printAmount(BalanceListManager.getFrozenEdgeBalanceMap()
                                .getOrDefault(ByteBuffer.wrap(transaction.getSenderIdentifier()), 0L)),
                        supplementalTransactionValid,
                        ByteUtil.arrayAsStringWithDashes(transaction.getSenderData())
                );
            } else {
                errors.add("Please provide a transaction.");
            }
        } catch (Exception e) {
            errors.add("Unexpected issue forwarding transaction: " + PrintUtil.printException(e));
        }

        return new SimpleExecutionResult(notices, errors, table);
    }

    public static void performMaintenance() {

        // Mark that maintenance has been performed.
        requestsSinceMaintenance.set(0);

        // Remove any transactions behind the frozen edge.
        long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
        for (String key : new HashSet<>(recentlyForwardedTransactions.keySet())) {
            Transaction transaction = recentlyForwardedTransactions.get(key);
            long transactionHeight = BlockManager.heightForTimestamp(transaction.getTimestamp());
            if (transactionHeight <= frozenEdgeHeight) {
                recentlyForwardedTransactions.remove(key);
            }
        }

        // If the map is still too large, remove arbitrary transactions.
        if (recentlyForwardedTransactions.size() > maximumMapSize) {
            Iterator<String> iterator = new HashSet<>(recentlyForwardedTransactions.keySet()).iterator();
            while (recentlyForwardedTransactions.size() > maximumMapSize) {
                recentlyForwardedTransactions.remove(iterator.next());
            }
        }
    }

    public static List<Transaction> transactionsForAccount(byte[] accountIdentifier, byte[] searchPrefix,
                                                           String senderReceiverFlag, long minimumTimestamp,
                                                           long maximumTimestamp, long minimumBlockHeight,
                                                           long maximumBlockHeight) {

        // Replace a null prefix with an empty prefix for later simplicity.
        if (searchPrefix == null) {
            searchPrefix = new byte[0];
        }

        // Determine whether to look for transactions for which this account is sender, receiver, or both. If the value
        // of this flag is "s" or "sender", only transactions for which the account is sender will be returned. If the
        // value is "r" or "receiver", only transactions for which the account is receiver will be returned. Otherwise,
        // both will be returned.
        senderReceiverFlag = senderReceiverFlag.toLowerCase();
        boolean acceptSender = !senderReceiverFlag.equals("r") && !senderReceiverFlag.equals("receiver");
        boolean acceptReceiver = !senderReceiverFlag.equals("s") && !senderReceiverFlag.equals("sender");

        // A maximum timestamp of less than zero means "no maximum", so set the maximum to Long.MAX_VALUE.
        if (maximumTimestamp < 0) {
            maximumTimestamp = Long.MAX_VALUE;
        }

        // If a minimum block height is specified, use it to modify the minimum timestamp. Using the greater of the
        // minimum timestamp and the timestamp indicated by the minimum block height effectively apply both values with
        // a logical AND.
        if (minimumBlockHeight >= 0) {
            minimumTimestamp = Math.max(minimumTimestamp, BlockManager.startTimestampForHeight(minimumBlockHeight));
        }

        // If a maximum block height is specified, use it to modify the maximum timestamp. This follows the same pattern
        // as the minimum block height.
        if (maximumBlockHeight >= 0) {
            maximumTimestamp = Math.min(maximumTimestamp,
                    BlockManager.startTimestampForHeight(maximumBlockHeight + 1L) - 1L);  // end of the block
        }

        // Find all transactions in the recently-forwarded set that match the given parameters.
        List<Transaction> transactions = new ArrayList<>();
        for (Transaction transaction : recentlyForwardedTransactions.values()) {

            boolean isSender = ByteUtil.arraysAreEqual(transaction.getSenderIdentifier(), accountIdentifier);
            boolean isReceiver = ByteUtil.arraysAreEqual(transaction.getReceiverIdentifier(), accountIdentifier);

            // Check the sender/receiver value.
            if ((acceptSender && isSender) || (acceptReceiver && isReceiver)) {

                // Check the timestamp.
                if (transaction.getTimestamp() >= minimumTimestamp && transaction.getTimestamp() <= maximumTimestamp) {

                    // Check the sender-data prefix.
                    boolean isPrefixMatch = true;
                    byte[] senderData = transaction.getSenderData();
                    for (int i = 0; i < Math.min(searchPrefix.length, senderData.length) && isPrefixMatch; i++) {
                        isPrefixMatch = searchPrefix[i] == senderData[i];
                    }

                    // If the prefix matches, add the transaction to the list.
                    if (isPrefixMatch) {
                        transactions.add(transaction);
                    }
                }
            }
        }

        return transactions;
    }

    public static Collection<Transaction> allTransactions() {
        return recentlyForwardedTransactions.values();
    }
}
