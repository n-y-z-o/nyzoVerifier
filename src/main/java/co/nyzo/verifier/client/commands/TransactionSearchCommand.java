package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.*;
import co.nyzo.verifier.util.PrintUtil;

import java.util.ArrayList;
import java.util.List;

public class TransactionSearchCommand implements Command {

    @Override
    public String getShortCommand() {
        return "TS";
    }

    @Override
    public String getLongCommand() {
        return "transactionSearch";
    }

    @Override
    public String getDescription() {
        return "search for transactions in the blockchain";
    }

    @Override
    public String[] getArgumentNames() {
        return new String[] { "timestamp (optional)", "block height (optional)" };
    }

    @Override
    public String[] getArgumentIdentifiers() {
        return new String[] { "timestamp", "blockHeight" };
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

        // Make lists for transactions, notices, and errors.
        List<String> notices = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Get the timestamp, if available. Interpret it as milliseconds by default.
        long timestamp = -1L;
        try {
            timestamp = Long.parseLong(argumentValues.get(0));
        } catch (Exception ignored) { }

        // if the timestamp value is not valid, try interpreting the timestamp as seconds.
        if (timestamp < BlockManager.getGenesisBlockStartTimestamp()) {
            try {
                timestamp = (long) (Double.parseDouble(argumentValues.get(0)) * 1000.0);
            } catch (Exception ignored) { }
        }

        // Get the block height, if available.
        long blockHeight = -1L;
        try {
            blockHeight = Long.parseLong(argumentValues.get(1));
        } catch (Exception ignored) { }

        // Set the search timestamp range from either the timestamp or block height.
        long minimumTimestamp = -1L;
        long maximumTimestamp = -1L;
        if (timestamp >= 0) {
            notices.add("Using timestamp of " + PrintUtil.printTimestamp(timestamp) + " for search");
            minimumTimestamp = timestamp;
            maximumTimestamp = timestamp;
        } else if (blockHeight >= 0) {
            notices.add("Using block height of " + blockHeight + " for search");
            minimumTimestamp = BlockManager.startTimestampForHeight(blockHeight);
            maximumTimestamp = BlockManager.startTimestampForHeight(blockHeight + 1L) - 1L;
        }

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
        if (minimumTimestamp > 0) {
            long height = BlockManager.heightForTimestamp(minimumTimestamp);
            Block block = BlockManager.frozenBlockForHeight(height);
            if (block == null) {
                block = HistoricalBlockManager.blockForHeight(height);
            }
            long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
            long retentionEdgeHeight = BlockManager.getRetentionEdgeHeight();
            List<Transaction> transactions = new ArrayList<>();
            if (block == null && height > frozenEdgeHeight) {
                errors.add("Block " + height + " is past the frozen edge, " + frozenEdgeHeight + ", on this system");
            } else if (block == null && height < BlockManager.getRetentionEdgeHeight()) {
                errors.add("Block " + height + " is behind the retention edge, " + retentionEdgeHeight +
                        ", on this system");
            } else if (block == null) {
                errors.add("Block " + height + " is not available on this system");
            } else {
                for (Transaction transaction : block.getTransactions()) {
                    if (transaction.getTimestamp() >= minimumTimestamp && transaction.getTimestamp() <=
                            maximumTimestamp) {
                        transactions.add(transaction);
                    }
                }

                if (transactions.isEmpty()) {
                    if (minimumTimestamp == maximumTimestamp) {
                        notices.add("No transactions found for timestamp " +
                                PrintUtil.printTimestamp(minimumTimestamp));
                    } else {
                        notices.add("No transactions found for height " + height);
                        notices.add("Minimum timestamp " + minimumTimestamp);
                        notices.add("Maximum timestamp " + maximumTimestamp);
                    }
                }

                for (Transaction transaction : transactions) {
                    table.addRow(BlockManager.heightForTimestamp(transaction.getTimestamp()) + "",
                            transaction.getTimestamp() + "", transaction.getType() + "",
                            typeString(transaction.getType()), PrintUtil.printAmount(transaction.getAmount()),
                            ByteUtil.arrayAsStringWithDashes(transaction.getReceiverIdentifier()),
                            ByteUtil.arrayAsStringWithDashes(transaction.getSenderIdentifier()),
                            transaction.getPreviousHashHeight() + "",
                            ClientTransactionUtil.senderDataForDisplay(transaction.getSenderData()) + "",
                            ByteUtil.arrayAsStringNoDashes(transaction.getSenderData()) + "");
                }
            }
        } else {
            errors.add("Unable to process query");
        }

        return new SimpleExecutionResult(table, notices, errors);
    }

    private static String typeString(byte type) {

        String[] types = { "coin generation", "seed", "standard", "cycle" };
        String result;
        if (type >= 0 && type < types.length) {
            result = types[type];
        } else {
            result = "unknown type (please implement this type in the TransactionSearchCommand.typeString() method)";
        }

        return result;
    }
}
