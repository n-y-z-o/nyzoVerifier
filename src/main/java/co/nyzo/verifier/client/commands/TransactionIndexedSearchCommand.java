package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.*;
import co.nyzo.verifier.nyzoString.NyzoStringEncoder;
import co.nyzo.verifier.nyzoString.NyzoStringPublicIdentifier;
import co.nyzo.verifier.util.PrintUtil;

import java.util.ArrayList;
import java.util.List;

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
        return new String[] { "account ID" };
    }

    @Override
    public String[] getArgumentIdentifiers() {
        return new String[] { "accountIdentifier" };
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

        // Add a notice reminding the user that the list of transactions may be incomplete due to incomplete indexing.
        notices.add("Results may be incomplete due to incomplete indexing");

        // Add notices showing the account ID in Nyzo string and raw hex formats.
        notices.add("account ID (raw): " + ByteUtil.arrayAsStringWithDashes(accountIdentifier.getIdentifier()));
        notices.add("account ID (Nyzo string): " + NyzoStringEncoder.encode(accountIdentifier));

        // Get the height and get the block. Produce appropriate errors and notices. Build the result table.
        CommandTable table = new CommandTable(new CommandTableHeader("height", "height"),
                new CommandTableHeader("timestamp (ms)", "timestampMilliseconds", true),
                new CommandTableHeader("type value", "typeValue"),
                new CommandTableHeader("type", "type"),
                new CommandTableHeader("amount", "amount"),
                new CommandTableHeader("receiver ID", "receiverIdentifier", true),
                new CommandTableHeader("sender ID", "senderIdentifier", true),
                new CommandTableHeader("previous hash height", "previousHashHeight"),
                new CommandTableHeader("sender data", "senderData"),
                new CommandTableHeader("sender data bytes", "senderDataBytes", true));

        List<Transaction> transactions = TransactionIndexer.transactionsForAccount(accountIdentifier.getIdentifier());
        for (Transaction transaction : transactions) {
            table.addRow(BlockManager.heightForTimestamp(transaction.getTimestamp()),
                    transaction.getTimestamp(), transaction.getType(),
                    TransactionSearchCommand.typeString(transaction.getType()),
                    PrintUtil.printAmount(transaction.getAmount()),
                    ByteUtil.arrayAsStringWithDashes(transaction.getReceiverIdentifier()),
                    ByteUtil.arrayAsStringWithDashes(transaction.getSenderIdentifier()),
                    transaction.getPreviousHashHeight(),
                    ClientTransactionUtil.senderDataForDisplay(transaction.getSenderData()),
                    ByteUtil.arrayAsStringNoDashes(transaction.getSenderData()));
        }

        return new SimpleExecutionResult(table, notices, errors);
    }
}
