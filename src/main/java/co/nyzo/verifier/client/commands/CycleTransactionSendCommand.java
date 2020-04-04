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
        return new String[] { "initiator key (in-cycle verifier)", "receiver ID", "sender data", "amount, Nyzos" };
    }

    @Override
    public String[] getArgumentIdentifiers() {
        return new String[] { "initiatorKey", "receiverId", "senderData", "amount" };
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
    public boolean isLongRunning() {
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

            // Check the receiver ID.
            NyzoString receiverIdentifier = NyzoStringEncoder.decode(argumentValues.get(1));
            if (receiverIdentifier instanceof NyzoStringPublicIdentifier) {
                argumentResults.add(new ArgumentResult(true, NyzoStringEncoder.encode(receiverIdentifier)));
            } else {
                String message = argumentValues.get(1).trim().isEmpty() ? "missing Nyzo string public ID" :
                        "not a valid Nyzo string public ID";
                argumentResults.add(new ArgumentResult(false, argumentValues.get(1), message));

                if (argumentValues.get(1).length() >= 64) {
                    PublicNyzoStringCommand.printHexWarning(output);
                }
            }

            // Process the sender data.
            String senderDataInputString = argumentValues.get(2);
            byte[] senderDataBytes;
            String senderDataMessage = "";
            String senderData;
            if (ClientTransactionUtil.isNormalizedSenderDataString(senderDataInputString)) {
                // This it the case for raw bytes sender data.
                senderDataBytes = ClientTransactionUtil.bytesFromNormalizedSenderDataString(senderDataInputString);
                senderDataMessage = "raw bytes";
                senderData = ClientTransactionUtil.normalizedSenderDataString(senderDataBytes);
            } else {
                // This is the case for plain text sender data.
                senderDataBytes = senderDataInputString.getBytes(StandardCharsets.UTF_8);
                if (senderDataBytes.length > FieldByteSize.maximumSenderDataLength) {
                    senderDataMessage = "sender data too long; truncating";
                    senderDataBytes = Arrays.copyOf(senderDataBytes, FieldByteSize.maximumSenderDataLength);
                }
                senderData = new String(senderDataBytes, StandardCharsets.UTF_8);
            }
            argumentResults.add(new ArgumentResult(true, senderData, senderDataMessage));

            // Check the amount.
            long amountMicronyzos = -1L;
            try {
                amountMicronyzos = (long) (Double.parseDouble(argumentValues.get(3)) *
                        Transaction.micronyzoMultiplierRatio);
            } catch (Exception ignored) { }
            if (amountMicronyzos > 0) {
                double amountNyzos = amountMicronyzos / (double) Transaction.micronyzoMultiplierRatio;
                argumentResults.add(new ArgumentResult(true, String.format("%.6f", amountNyzos), ""));
            } else {
                argumentResults.add(new ArgumentResult(false, argumentValues.get(3), "invalid amount"));
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

        try {
            // Get the arguments.
            NyzoStringPrivateSeed signerSeed = (NyzoStringPrivateSeed) NyzoStringEncoder.decode(argumentValues.get(0));
            NyzoStringPublicIdentifier receiverIdentifier =
                    (NyzoStringPublicIdentifier) NyzoStringEncoder.decode(argumentValues.get(1));
            String senderDataString = argumentValues.get(2);
            byte[] senderData;
            if (ClientTransactionUtil.isNormalizedSenderDataString(senderDataString)) {
                senderData = ClientTransactionUtil.bytesFromNormalizedSenderDataString(senderDataString);
            } else {
                senderData = senderDataString.getBytes(StandardCharsets.UTF_8);
            }
            long amount = (long) (Double.parseDouble(argumentValues.get(3)) * Transaction.micronyzoMultiplierRatio);

            // Create the transaction and send to likely verifiers.
            long timestamp = ClientTransactionUtil.suggestedTransactionTimestamp();
            Transaction transaction = Transaction.cycleTransaction(timestamp, amount,
                    receiverIdentifier.getIdentifier(), senderData, signerSeed.getSeed());
            ClientTransactionUtil.sendTransactionToLikelyBlockVerifiers(transaction, true, output);
        } catch (Exception e) {
            output.println(ConsoleColor.Red + "unexpected issue creating cycle transaction: " +
                    PrintUtil.printException(e) + ConsoleColor.reset);
        }

        return null;
    }
}
