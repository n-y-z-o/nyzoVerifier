package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.KeyUtil;
import co.nyzo.verifier.Transaction;
import co.nyzo.verifier.client.*;
import co.nyzo.verifier.nyzoString.*;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PrefilledDataSendCommand implements Command {

    @Override
    public String getShortCommand() {
        return "PRS";
    }

    @Override
    public String getLongCommand() {
        return "sendPrefill";
    }

    @Override
    public String getDescription() {
        return "send a prefilled-data transaction";
    }

    @Override
    public String[] getArgumentNames() {
        return new String[] { "sender key", "prefilled-data string", "amount, Nyzos" };
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
    public ValidationResult validate(List<String> argumentValues) {

        ValidationResult result = null;
        try {
            // Make a list for the argument result items.
            List<ArgumentResult> argumentResults = new ArrayList<>();

            // Check the sender key.
            NyzoString senderKey = NyzoStringEncoder.decode(argumentValues.get(0));
            if (senderKey instanceof NyzoStringPrivateSeed) {
                argumentResults.add(new ArgumentResult(true, NyzoStringEncoder.encode(senderKey)));
            } else {
                String message = argumentValues.get(0).trim().isEmpty() ? "missing Nyzo string private key" :
                        "not a valid Nyzo string private key";
                argumentResults.add(new ArgumentResult(false, argumentValues.get(0), message));

                if (argumentValues.get(0).length() >= 64) {
                    PrivateNyzoStringCommand.printHexWarning();
                }
            }

            // Check the prefilled-data string.
            NyzoString string = NyzoStringEncoder.decode(argumentValues.get(1));
            if (string instanceof NyzoStringPrefilledData) {
                // Show the receiver and sender data. This is not necessary for validation, but it is helpful for
                // confirmation, because the confirmation process does not show the separate parts of the string.
                NyzoStringPrefilledData prefilledData = (NyzoStringPrefilledData) string;
                List<String> labels = Arrays.asList("receiver ID", "sender data");
                List<String> values = Arrays.asList(
                        NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(prefilledData.getReceiverIdentifier())),
                        new String(prefilledData.getSenderData(), StandardCharsets.UTF_8));
                ConsoleUtil.printTable(Arrays.asList(labels, values));

                // Check that the sender and receiver are different. If so, this argument is valid.
                if (senderKey != null && ByteUtil.arraysAreEqual(prefilledData.getReceiverIdentifier(),
                        KeyUtil.identifierForSeed(senderKey.getBytes()))) {
                    argumentResults.add(new ArgumentResult(false, NyzoStringEncoder.encode(prefilledData),
                            "sender and receiver are same"));
                } else {
                    argumentResults.add(new ArgumentResult(true, NyzoStringEncoder.encode(prefilledData), ""));
                }
            } else {
                String message = argumentValues.get(1).trim().isEmpty() ? "missing prefilled-data string" :
                        "not a valid prefilled-data string";
                argumentResults.add(new ArgumentResult(false, argumentValues.get(1), message));
            }

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
    public void run(List<String> argumentValues) {

        try {
            // Get the arguments.
            NyzoStringPrivateSeed signerSeed = (NyzoStringPrivateSeed) NyzoStringEncoder.decode(argumentValues.get(0));
            NyzoStringPrefilledData prefilledData  =
                    (NyzoStringPrefilledData) NyzoStringEncoder.decode(argumentValues.get(1));
            long amount = (long) (Double.parseDouble(argumentValues.get(2)) * Transaction.micronyzoMultiplierRatio);

            // Send the transaction to the cycle.
            ClientTransactionUtil.createAndSendTransaction(signerSeed,
                    new NyzoStringPublicIdentifier(prefilledData.getReceiverIdentifier()),
                    prefilledData.getSenderData(), amount);
        } catch (Exception e) {
            System.out.println(ConsoleColor.Red + "unexpected issue creating transaction: " +
                    PrintUtil.printException(e) + ConsoleColor.reset);
        }
    }
}
