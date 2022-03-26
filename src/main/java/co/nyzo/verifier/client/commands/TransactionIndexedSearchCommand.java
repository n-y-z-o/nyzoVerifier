package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.*;
import co.nyzo.verifier.nyzoString.NyzoStringEncoder;
import co.nyzo.verifier.nyzoString.NyzoStringPublicIdentifier;
import co.nyzo.verifier.util.PrintUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TransactionIndexedSearchCommand implements Command {

    @Override
    public String getShortCommand() {
        return "IS";
    }

    @Override
    public String getLongCommand() {
        return "indexedSearch";
    }

    @Override
    public String getDescription() {
        return "search for transactions in an account";
    }

    @Override
    public String[] getArgumentNames() {
        return new String[] { "account ID", "sender data prefix (optional)", "sender/receiver (s/r, optional)",
                "minimum timestamp (optional)", "maximum timestamp (optional)", "minimum block height (optional)",
                "maximum block height (optional)" };
    }

    @Override
    public String[] getArgumentIdentifiers() {
        return new String[] { "accountIdentifier", "senderDataPrefix", "senderReceiverFlag", "minimumTimestamp",
                "maximumTimestamp", "minimumBlockHeight", "maximumBlockHeight" };
    }

    @Override
    public boolean requiresValidation() {
        return false;
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
        return null;
    }

    @Override
    public ExecutionResult run(List<String> argumentValues, CommandOutput output) {

        // Make lists for notices and errors.
        List<String> notices = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Get the argument values.
        NyzoStringPublicIdentifier accountIdentifier = ClientArgumentUtil.getPublicIdentifier(argumentValues.get(0));
        byte[] senderDataPrefix = ClientArgumentUtil.getSenderData(argumentValues.get(1));
        String senderReceiverFlag = argumentValues.get(2);
        long minimumTimestamp = ClientArgumentUtil.getLong(argumentValues.get(3), -1L);
        long maximumTimestamp = ClientArgumentUtil.getLong(argumentValues.get(4), -1L);
        long minimumBlockHeight = ClientArgumentUtil.getLong(argumentValues.get(5), -1L);
        long maximumBlockHeight = ClientArgumentUtil.getLong(argumentValues.get(6), -1L);

        // Add a notice reminding the user that the list of transactions may be incomplete due to incomplete indexing.
        notices.add("Results may be incomplete due to incomplete indexing");

        // Get the height and get the block. Produce appropriate errors and notices. Build the transaction table.
        CommandTable transactionTable = new CommandTable(new CommandTableHeader("height", "height"),
                new CommandTableHeader("timestamp (ms)", "timestampMilliseconds", true),
                new CommandTableHeader("type value", "typeValue"),
                new CommandTableHeader("type", "type"),
                new CommandTableHeader("amount", "amount"),
                new CommandTableHeader("receiver ID", "receiverIdentifier", true),
                new CommandTableHeader("sender ID", "senderIdentifier", true),
                new CommandTableHeader("previous hash height", "previousHashHeight"),
                new CommandTableHeader("sender data", "senderData"),
                new CommandTableHeader("sender data bytes", "senderDataBytes", true));

        List<Transaction> transactions = TransactionIndexer.transactionsForAccount(accountIdentifier.getIdentifier(),
                senderDataPrefix, senderReceiverFlag, minimumTimestamp, maximumTimestamp, minimumBlockHeight,
                maximumBlockHeight);
        for (Transaction transaction : transactions) {
            transactionTable.addRow(BlockManager.heightForTimestamp(transaction.getTimestamp()),
                    transaction.getTimestamp(), transaction.getType(),
                    TransactionSearchCommand.typeString(transaction.getType()),
                    PrintUtil.printAmount(transaction.getAmount()),
                    ByteUtil.arrayAsStringWithDashes(transaction.getReceiverIdentifier()),
                    ByteUtil.arrayAsStringWithDashes(transaction.getSenderIdentifier()),
                    transaction.getPreviousHashHeight(),
                    ClientTransactionUtil.senderDataForDisplay(transaction.getSenderData()),
                    ByteUtil.arrayAsStringNoDashes(transaction.getSenderData()));
        }

        // Build the supplemental table.
        CommandTable supplementalTable = new CommandTable(
                new CommandTableHeader("account ID (raw)", "accountIdentifierRaw", true),
                new CommandTableHeader("account ID (Nyzo string)", "accountIdentifierNyzoString", true));
        supplementalTable.setInvertedRowsColumns(true);
        supplementalTable.addRow(ByteUtil.arrayAsStringWithDashes(accountIdentifier.getIdentifier()),
                NyzoStringEncoder.encode(accountIdentifier));

        return new SimpleExecutionResult(notices, errors, transactionTable, supplementalTable);
    }
}
