package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.*;
import co.nyzo.verifier.nyzoString.*;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.web.WebUtil;

import java.util.*;

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
        return new String[0];
    }

    @Override
    public String[] getArgumentIdentifiers() {
        return new String[0];
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

        // Make the lists for the notices and errors. Make the result table.
        List<String> notices = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        CommandTable table = new CommandTable(new CommandTableHeader("initiator ID", "initiatorId", true),
                new CommandTableHeader("initiator ID string", "initiatorIdNyzoString", true),
                new CommandTableHeader("receiver ID", "receiverId", true),
                new CommandTableHeader("receiver ID string", "receiverIdNyzoString", true),
                new CommandTableHeader("amount", "amount"),
                new CommandTableHeader("height", "height"),
                new CommandTableHeader("initiator data", "initiatorData", true),
                new CommandTableHeader("# votes", "numberOfVotes"),
                new CommandTableHeader("# yes votes", "numberOfYesVotes"),
                new CommandTableHeader("signature", "signature", true),
                new CommandTableHeader("signature string", "signatureNyzoString", true));

        try {
            // Get the balance list.
            BalanceList balanceList = BalanceListManager.getFrozenEdgeList();

            if (balanceList == null) {
                errors.add("balance list is null");
            } else if (balanceList.getPendingCycleTransactions().isEmpty()) {
                notices.add("no cycle transactions in balance list");
            } else {
                long distanceFromOpen = BlockManager.openEdgeHeight(false) - balanceList.getBlockHeight();
                notices.add("using balance list at height " + balanceList.getBlockHeight() + ", " + distanceFromOpen +
                        " from open edge");
                for (Transaction transaction : balanceList.getPendingCycleTransactions().values()) {
                    List<Object> row = new ArrayList<>();

                    // Add the sender identifier columns.
                    byte[] senderIdentifier = transaction.getSenderIdentifier();
                    row.add(ByteUtil.arrayAsStringWithDashes(senderIdentifier));
                    row.add(NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(senderIdentifier)));

                    // Add the receiver identifier columns.
                    byte[] receiverIdentifier = transaction.getReceiverIdentifier();
                    row.add(ByteUtil.arrayAsStringWithDashes(receiverIdentifier));
                    row.add(NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(receiverIdentifier)));

                    // Add the amount, height, initiator data, number of cycle signatures, and number of "yes" votes.
                    row.add(PrintUtil.printAmount(transaction.getAmount()));
                    row.add(BlockManager.heightForTimestamp(transaction.getTimestamp()));
                    row.add(WebUtil.sanitizedSenderDataForDisplay(transaction.getSenderData()));
                    row.add(transaction.getCycleSignatureTransactions().size());
                    row.add(numberOfYesVotes(transaction.getCycleSignatureTransactions().values()));

                    // Add the signature columns.
                    row.add(ByteUtil.arrayAsStringWithDashes(transaction.getSignature()));
                    row.add(NyzoStringEncoder.encode(new NyzoStringSignature(transaction.getSignature())));

                    // Add the row to the table.
                    table.addRow(row.toArray());
                }
            }

        } catch (Exception e) {
            output.println(ConsoleColor.Red + "unexpected issue listing cycle transactions: " +
                    PrintUtil.printException(e) + ConsoleColor.reset);
        }

        return new SimpleExecutionResult(notices, errors, table);
    }

    private static int numberOfYesVotes(Collection<Transaction> signatureTransactions) {
        int numberOfYesVotes = 0;
        for (Transaction transaction : signatureTransactions) {
            if (transaction.getCycleTransactionVote() == 1) {
                numberOfYesVotes++;
            }
        }

        return numberOfYesVotes;
    }
}
