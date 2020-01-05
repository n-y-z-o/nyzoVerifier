package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.*;
import co.nyzo.verifier.nyzoString.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class PrefilledDataCreateCommand implements Command {
    @Override
    public String getShortCommand() {
        return "PRC";
    }

    @Override
    public String getLongCommand() {
        return "createPrefill";
    }

    @Override
    public String getDescription() {
        return "create a Nyzo prefilled-data string";
    }

    @Override
    public String[] getArgumentNames() {
        return new String[] { "receiver ID", "sender data" };
    }

    @Override
    public String[] getArgumentIdentifiers() {
        return new String[] { "receiverId", "senderData" };
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

            // Check the receiver ID.
            NyzoString receiverIdentifier = NyzoStringEncoder.decode(argumentValues.get(0));
            if (receiverIdentifier instanceof NyzoStringPublicIdentifier) {
                argumentResults.add(new ArgumentResult(true, NyzoStringEncoder.encode(receiverIdentifier), ""));
            } else {
                String message = argumentValues.get(0).trim().isEmpty() ? "missing Nyzo string public ID" :
                        "not a valid Nyzo string public ID";
                argumentResults.add(new ArgumentResult(false, argumentValues.get(0), message));

                if (argumentValues.get(0).length() >= 64) {
                    PublicNyzoStringCommand.printHexWarning(output);
                }
            }

            // Process the sender data.
            byte[] senderDataBytes = argumentValues.get(1).getBytes(StandardCharsets.UTF_8);
            if (senderDataBytes.length > FieldByteSize.maximumSenderDataLength) {
                output.println(ConsoleColor.Yellow + "sender data too long; truncating" + ConsoleColor.reset);
                senderDataBytes = Arrays.copyOf(senderDataBytes, FieldByteSize.maximumSenderDataLength);
            }
            String senderData = new String(senderDataBytes, StandardCharsets.UTF_8);
            argumentResults.add(new ArgumentResult(true, senderData, ""));

            // Produce the result.
            result = new ValidationResult(argumentResults);

        } catch (Exception e) {
            e.printStackTrace();
        }

        // If the confirmation result is null, create an exception result. This will only happen if an exception is not
        // handled properly by the confirmation code.
        if (result == null) {
            result = ValidationResult.exceptionResult(getArgumentNames().length);
        }

        return result;
    }

    @Override
    public ExecutionResult run(List<String> argumentValues, CommandOutput output) {

        // Get the arguments.
        NyzoStringPublicIdentifier receiverIdentifier =
                (NyzoStringPublicIdentifier) NyzoStringEncoder.decode(argumentValues.get(0));
        byte[] senderData = argumentValues.get(1).getBytes(StandardCharsets.UTF_8);

        // If this is not a known identifier in the latest balance list, add a notice.
        List<String> notices = new ArrayList<>();
        BalanceList frozenEdgeList = BalanceListManager.getFrozenEdgeList();
        if (frozenEdgeList != null) {
            List<BalanceListItem> balanceListItems = frozenEdgeList.getItems();
            boolean foundInBalanceList = false;
            for (int i = 0; i < balanceListItems.size() && !foundInBalanceList; i++) {
                foundInBalanceList = ByteUtil.arraysAreEqual(balanceListItems.get(i).getIdentifier(),
                        receiverIdentifier.getBytes());
            }
            if (!foundInBalanceList) {
                notices.add("This account was not found in the balance list at height " +
                        frozenEdgeList.getBlockHeight() + ". If the ID you provided is incorrect, and you send coins " +
                        "to it, those coins will likely be unrecoverable. Please ensure that this address is valid " +
                        "before sending coins.");
            }
        }

        // Make the prefilled-data string.
        NyzoStringPrefilledData prefilledDataString = new NyzoStringPrefilledData(receiverIdentifier.getBytes(),
                senderData);

        // Build the output table. Note that the individual fields are retrieved from the prefilled-data string.
        CommandTable table = new CommandTable(new CommandTableHeader("receiver ID", "receiverId", true),
                new CommandTableHeader("sender data", "senderData", true),
                new CommandTableHeader("prefilled-data string", "prefilledDataString", true));
        table.setInvertedRowsColumns(true);
        table.addRow(
                NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(prefilledDataString.getReceiverIdentifier())),
                new String(prefilledDataString.getSenderData(), StandardCharsets.UTF_8),
                NyzoStringEncoder.encode(prefilledDataString));

        // Produce the execution result.
        return new SimpleExecutionResult(table, notices, null);
    }
}
