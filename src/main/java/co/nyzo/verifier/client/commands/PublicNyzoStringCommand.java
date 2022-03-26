package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.*;
import co.nyzo.verifier.nyzoString.NyzoStringEncoder;
import co.nyzo.verifier.nyzoString.NyzoStringPublicIdentifier;

import java.util.*;

public class PublicNyzoStringCommand implements Command {

    @Override
    public String getShortCommand() {
        return "NIS";
    }

    @Override
    public String getLongCommand() {
        return "idString";
    }

    @Override
    public String getDescription() {
        return "create Nyzo string for a public ID";
    }

    @Override
    public String[] getArgumentNames() {
        return new String[] { "public ID" };
    }

    @Override
    public String[] getArgumentIdentifiers() {
        return new String[] { "publicId" };
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

        // Get the identifier from the arguments.
        byte[] publicIdentifier = ByteUtil.byteArrayFromHexString(argumentValues.get(0), FieldByteSize.identifier);

        // If this is not a known identifier in the latest balance list, add a notice.
        List<String> notices = new ArrayList<>();
        BalanceList frozenEdgeList = BalanceListManager.getFrozenEdgeList();
        if (frozenEdgeList != null) {
            List<BalanceListItem> balanceListItems = frozenEdgeList.getItems();
            boolean foundInBalanceList = false;
            for (int i = 0; i < balanceListItems.size() && !foundInBalanceList; i++) {
                foundInBalanceList = ByteUtil.arraysAreEqual(balanceListItems.get(i).getIdentifier(), publicIdentifier);
            }
            if (!foundInBalanceList) {
                notices.add("This account was not found in the balance list at height " +
                        frozenEdgeList.getBlockHeight() + ". If the ID you provided is incorrect, and you send coins " +
                        "to it, those coins will likely be unrecoverable. Please ensure that this address is valid " +
                        "before sending coins.");
            }
        }

        // Build the output table.
        NyzoStringPublicIdentifier publicIdentifierString = new NyzoStringPublicIdentifier(publicIdentifier);
        CommandTable table = new CommandTable(new CommandTableHeader("public ID (raw)", "publicIdBytes", true),
                new CommandTableHeader("public ID (Nyzo string)", "publicIdNyzoString", true));
        table.setInvertedRowsColumns(true);
        table.addRow(ByteUtil.arrayAsStringWithDashes(publicIdentifier),
                NyzoStringEncoder.encode(publicIdentifierString));

        // Produce the execution result.
        return new SimpleExecutionResult(notices, null, table);
    }

    public static void printHexWarning(CommandOutput output) {
        PublicNyzoStringCommand command = new PublicNyzoStringCommand();
        output.println(ConsoleColor.Yellow.background() + "You appear to be using a raw hexadecimal " +
                "public ID. Please convert this to a Nyzo string with the \"" + command.getLongCommand() +
                "\" (" + command.getShortCommand() + ") command." + ConsoleColor.reset);
    }
}
