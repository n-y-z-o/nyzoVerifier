package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.*;
import co.nyzo.verifier.nyzoString.NyzoString;
import co.nyzo.verifier.nyzoString.NyzoStringEncoder;
import co.nyzo.verifier.nyzoString.NyzoStringPrivateSeed;
import co.nyzo.verifier.nyzoString.NyzoStringPublicIdentifier;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CycleTransactionSendCommand implements Command {

    @Override
    public String getShortCommand() {
        return "CTS";
    }

    @Override
    public String getLongCommand() {
        return "cycleSend";
    }

    @Override
    public String getDescription() {
        return "send a cycle transaction";
    }

    @Override
    public String[] getArgumentNames() {
        return new String[] { "initiator key (in-cycle verifier)", "block height", "receiver ID", "sender data",
                "amount, Nyzos" };
    }

    @Override
    public boolean requiresValidation() {
        return true;
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public ValidationResult validate(List<String> argumentValues, CommandOutput output) {

        ValidationResult result = null;
        try {
            // Make a list for the argument result items.
            List<ArgumentResult> argumentResults = new ArrayList<>();

            // Check the initiator key.
            NyzoString initiatorKey = NyzoStringEncoder.decode(argumentValues.get(0));
            if (initiatorKey instanceof NyzoStringPrivateSeed) {
                argumentResults.add(new ArgumentResult(true, NyzoStringEncoder.encode(initiatorKey)));
            } else {
                String message = argumentValues.get(0).trim().isEmpty() ? "missing Nyzo string private key" :
                        "not a valid Nyzo string private key";
                argumentResults.add(new ArgumentResult(false, argumentValues.get(0), message));

                if (argumentValues.get(0).length() >= 64) {
                    PrivateNyzoStringCommand.printHexWarning(output);
                }
            }

            // Check the block height. It must be past the open edge.
            long blockHeight = -1L;
            try {
                blockHeight = Long.parseLong(argumentValues.get(1));
            } catch (Exception ignored) { }
            if (blockHeight > BlockManager.openEdgeHeight(false)) {
                argumentResults.add(new ArgumentResult(true, blockHeight + "", ""));
            } else if (blockHeight > 0) {
                argumentResults.add(new ArgumentResult(false, blockHeight + "", "block height must be past open edge"));
            } else {
                argumentResults.add(new ArgumentResult(false, argumentValues.get(1), "invalid block height"));
            }

            // Check the receiver ID.
            NyzoString receiverIdentifier = NyzoStringEncoder.decode(argumentValues.get(2));
            if (receiverIdentifier instanceof NyzoStringPublicIdentifier) {
                argumentResults.add(new ArgumentResult(true, NyzoStringEncoder.encode(receiverIdentifier)));
            } else {
                String message = argumentValues.get(2).trim().isEmpty() ? "missing Nyzo string public ID" :
                        "not a valid Nyzo string public ID";
                argumentResults.add(new ArgumentResult(false, argumentValues.get(2), message));

                if (argumentValues.get(2).length() >= 64) {
                    PublicNyzoStringCommand.printHexWarning(output);
                }
            }

            // Process the sender data.
            byte[] senderDataBytes = argumentValues.get(3).getBytes(StandardCharsets.UTF_8);
            if (senderDataBytes.length > FieldByteSize.maximumSenderDataLength) {
                output.println(ConsoleColor.Yellow + "sender data too long; truncating" + ConsoleColor.reset);
                senderDataBytes = Arrays.copyOf(senderDataBytes, FieldByteSize.maximumSenderDataLength);
            }
            String senderData = new String(senderDataBytes, StandardCharsets.UTF_8);
            argumentResults.add(new ArgumentResult(true, senderData, ""));

            // Check the amount.
            long amountMicronyzos = -1L;
            try {
                amountMicronyzos = (long) (Double.parseDouble(argumentValues.get(4)) *
                        Transaction.micronyzoMultiplierRatio);
            } catch (Exception ignored) { }
            if (amountMicronyzos > 0) {
                double amountNyzos = amountMicronyzos / (double) Transaction.micronyzoMultiplierRatio;
                argumentResults.add(new ArgumentResult(true, String.format("%.6f", amountNyzos), ""));
            } else {
                argumentResults.add(new ArgumentResult(false, argumentValues.get(4), "invalid amount"));
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
    public void run(List<String> argumentValues, CommandOutput output) {

        try {
            // Get the arguments.
            NyzoStringPrivateSeed signerSeed = (NyzoStringPrivateSeed) NyzoStringEncoder.decode(argumentValues.get(0));
            long blockHeight = Long.parseLong(argumentValues.get(1));
            NyzoStringPublicIdentifier receiverIdentifier =
                    (NyzoStringPublicIdentifier) NyzoStringEncoder.decode(argumentValues.get(2));
            byte[] senderData = argumentValues.get(3).getBytes(StandardCharsets.UTF_8);
            long amount = (long) (Double.parseDouble(argumentValues.get(4)) * Transaction.micronyzoMultiplierRatio);

            // Create the transaction and send to the cycle.
            long timestamp = BlockManager.startTimestampForHeight(blockHeight) + 1000L;
            Transaction transaction = Transaction.cycleTransaction(timestamp, amount,
                    receiverIdentifier.getIdentifier(), senderData, signerSeed.getSeed());
            ClientTransactionUtil.sendCycleTransaction(transaction, output);
        } catch (Exception e) {
            output.println(ConsoleColor.Red + "unexpected issue creating cycle transaction: " +
                    PrintUtil.printException(e) + ConsoleColor.reset);
        }
    }
}
