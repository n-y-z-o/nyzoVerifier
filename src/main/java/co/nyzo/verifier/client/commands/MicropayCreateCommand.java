package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.ArgumentResult;
import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.client.ConsoleUtil;
import co.nyzo.verifier.client.ValidationResult;
import co.nyzo.verifier.nyzoString.*;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class MicropayCreateCommand implements Command {

    @Override
    public String getShortCommand() {
        return "MPC";
    }

    @Override
    public String getLongCommand() {
        return "createMicropay";
    }

    @Override
    public String getDescription() {
        return "create a Nyzo Micropay string";
    }

    @Override
    public String[] getArgumentNames() {
        return new String[] { "receiver ID", "sender data (optional)", "amount", "receiver IP", "receiver port" };
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
                senderDataBytes = Arrays.copyOf(senderDataBytes, FieldByteSize.maximumSenderDataLength);
            }
            String senderData = new String(senderDataBytes, StandardCharsets.UTF_8);
            argumentResults.add(new ArgumentResult(true, senderData, ""));

            // Check the amount.
            long amountMicronyzos = -1L;
            try {
                amountMicronyzos = (long) (Double.parseDouble(argumentValues.get(2)) *
                        Transaction.micronyzoMultiplierRatio);
            } catch (Exception ignored) { }
            if (amountMicronyzos > 0) {
                double amountNyzos = amountMicronyzos / (double) Transaction.micronyzoMultiplierRatio;
                argumentResults.add(new ArgumentResult(true, String.format("%.6f", amountNyzos), ""));
            } else {
                argumentResults.add(new ArgumentResult(false, argumentValues.get(2), "invalid amount"));
            }

            // Check the IP address.
            byte[] ipAddress = IpUtil.addressFromString(argumentValues.get(3));
            if (ipAddress == null || ByteUtil.isAllZeros(ipAddress)) {
                String message = argumentValues.get(3).trim().isEmpty() ? "missing receiver IP" :
                        "invalid IP address";
                argumentResults.add(new ArgumentResult(false, argumentValues.get(3), message));
            } else {
                argumentResults.add(new ArgumentResult(true, IpUtil.addressAsString(ipAddress), ""));
            }

            // Check the port.
            int port = -1;
            try {
                port = Integer.parseInt(argumentValues.get(4));
            } catch (Exception ignored) { }
            if (port <= 0) {
                String message = argumentValues.get(4).trim().isEmpty() ? "missing receiver port" : "invalid port";
                argumentResults.add(new ArgumentResult(false, argumentValues.get(4), message));
            } else {
                argumentResults.add(new ArgumentResult(true, port + "", ""));
            }

            // Produce the result.
            result = new ValidationResult(argumentResults);

        } catch (Exception e) {
            e.printStackTrace();
        }

        // If the confirmation result is null, create an exception result. This will only happen if an exception is not
        // handled properly by the validation code.
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
        long amountMicronyzos = (long) (Double.parseDouble(argumentValues.get(2)) *
                Transaction.micronyzoMultiplierRatio);
        byte[] ipAddress = IpUtil.addressFromString(argumentValues.get(3));
        int port = Integer.parseInt(argumentValues.get(4));

        // Make the Micropay string.
        NyzoStringMicropay micropayString = new NyzoStringMicropay(receiverIdentifier.getBytes(), senderData,
                amountMicronyzos, ipAddress, port);

        // Print the table. Note that the individual fields are retrieved from the Micropay string.
        List<String> labels = Arrays.asList("receiver ID", "sender data", "amount", "IP address", "port", "Micropay");
        List<String> values = Arrays.asList(
                NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(micropayString.getReceiverIdentifier())),
                new String(micropayString.getSenderData(), StandardCharsets.UTF_8),
                PrintUtil.printAmount(micropayString.getAmount()),
                IpUtil.addressAsString(micropayString.getReceiverIpAddress()), micropayString.getReceiverPort() + "",
                NyzoStringEncoder.encode(micropayString));

        ConsoleUtil.printTable(Arrays.asList(labels, values), new HashSet<>(Collections.singleton(4)));

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
