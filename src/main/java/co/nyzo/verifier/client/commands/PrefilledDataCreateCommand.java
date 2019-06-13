package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.ArgumentResult;
import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.client.ConsoleUtil;
import co.nyzo.verifier.client.ValidationResult;
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

            // Check the receiver ID.
            NyzoString receiverIdentifier = NyzoStringEncoder.decode(argumentValues.get(0));
            if (receiverIdentifier instanceof NyzoStringPublicIdentifier) {
                argumentResults.add(new ArgumentResult(true, NyzoStringEncoder.encode(receiverIdentifier), ""));
            } else {
                String message = argumentValues.get(0).trim().isEmpty() ? "missing Nyzo string public ID" :
                        "not a valid Nyzo string public ID";
                argumentResults.add(new ArgumentResult(false, argumentValues.get(0), message));

                if (argumentValues.get(0).length() >= 64) {
                    PublicNyzoStringCommand.printHexWarning();
                }
            }

            // Process the sender data.
            byte[] senderDataBytes = argumentValues.get(1).getBytes(StandardCharsets.UTF_8);
            if (senderDataBytes.length > FieldByteSize.maximumSenderDataLength) {
                System.out.println(ConsoleColor.Yellow + "sender data too long; truncating" + ConsoleColor.reset);
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
    public void run(List<String> argumentValues) {

        // Get the arguments.
        NyzoStringPublicIdentifier receiverIdentifier =
                (NyzoStringPublicIdentifier) NyzoStringEncoder.decode(argumentValues.get(0));
        byte[] senderData = argumentValues.get(1).getBytes(StandardCharsets.UTF_8);

        // Make the prefilled-data string.
        NyzoStringPrefilledData prefilledDataString = new NyzoStringPrefilledData(receiverIdentifier.getBytes(),
                senderData);

        // Print the table. Note that the individual fields are retrieved from the prefilled-data string.
        List<String> labels = Arrays.asList("receiver ID", "sender data", "prefilled-data string");
        List<String> values = Arrays.asList(
                NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(prefilledDataString.getReceiverIdentifier())),
                new String(prefilledDataString.getSenderData(), StandardCharsets.UTF_8),
                NyzoStringEncoder.encode(prefilledDataString));

        ConsoleUtil.printTable(Arrays.asList(labels, values), new HashSet<>(Collections.singleton(1)));

        // If this is not a known identifier in the latest balance list, display a warning.
        BalanceList frozenEdgeList = BalanceListManager.getFrozenEdgeList();
        List<BalanceListItem> balanceListItems = frozenEdgeList.getItems();
        boolean foundInBalanceList = false;
        for (int i = 0; i < balanceListItems.size() && !foundInBalanceList; i++) {
            foundInBalanceList = ByteUtil.arraysAreEqual(balanceListItems.get(i).getIdentifier(),
                    receiverIdentifier.getBytes());
        }
        if (!foundInBalanceList) {
            String color = ConsoleColor.Red.toString();
            String reset = ConsoleColor.reset;
            List<String> warning = Arrays.asList(
                    color + "This account was not found in the balance list at height " +
                            frozenEdgeList.getBlockHeight() + "." + reset,
                    color + "If the ID you provided is incorrect, and you send coins to it," + reset,
                    color + "those coins will likely be unrecoverable. Please ensure that this" + reset,
                    color + "address is valid before sending coins." + reset
            );
            ConsoleUtil.printTable(Collections.singletonList(warning));
        }
    }
}
