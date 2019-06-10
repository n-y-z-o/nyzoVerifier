package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.*;
import co.nyzo.verifier.messages.TransactionResponse;
import co.nyzo.verifier.nyzoString.*;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PreferencesUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.ThreadUtil;

import java.awt.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

public class MicropaySendCommand implements Command {

    private static final String micropaySenderSeedKey = "micropay_sender_key";
    private static final String micropayMaximumAmountKey = "micropay_max_amount_nyzos";

    @Override
    public String getShortCommand() {
        return "PAY";
    }

    @Override
    public String getLongCommand() {
        return "sendMicropay";
    }

    @Override
    public String getDescription() {
        return "send a Micropay transaction";
    }

    @Override
    public String[] getArgumentNames() {
        return new String[] { "Micropay string" };
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

            // Check the Micropay string.
            NyzoString string = NyzoStringEncoder.decode(argumentValues.get(0));
            if (string instanceof NyzoStringMicropay) {
                // Show the receiver, sender data, amount, and IP address. This is not necessary for validation, but it
                // is helpful for confirmation, because the confirmation process does not show the separate parts of the
                // Micropay string.
                NyzoStringMicropay micropay = (NyzoStringMicropay) string;
                List<String> labels = Arrays.asList("receiver ID", "sender data", "amount", "IP address", "port");
                List<String> values = Arrays.asList(
                        NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(micropay.getReceiverIdentifier())),
                        new String(micropay.getSenderData(), StandardCharsets.UTF_8),
                        PrintUtil.printAmount(micropay.getAmount()),
                        IpUtil.addressAsString(micropay.getReceiverIpAddress()), micropay.getReceiverPort() + "");
                ConsoleUtil.printTable(Arrays.asList(labels, values));

                // Ensure the Micropay sender and amount are set and the micropay transaction does not exceed the
                // threshold.
                boolean valid;
                String message;
                long maximumAmount = getMaximumTransactionAmountMicronyzos();
                if (getSenderSeed() == null) {
                    System.out.println(ConsoleColor.Red + "please set a valid Micropay sender key in preferences (" +
                            micropaySenderSeedKey + ")" + ConsoleColor.reset);
                    valid = false;
                    message = "sender not set";
                } else if (maximumAmount < 1) {
                    System.out.println(ConsoleColor.Red + "please set a valid Micropay maximum amount in " +
                            "preferences (" + micropayMaximumAmountKey + ")" + ConsoleColor.reset);
                    valid = false;
                    message = "amount not set";
                } else if (micropay.getAmount() > maximumAmount) {
                    valid = false;
                    message = "amount exceeds maximum";
                    System.out.println(ConsoleColor.Red + "amount " + PrintUtil.printAmount(micropay.getAmount()) +
                            " exceeds Micropay maximum " + PrintUtil.printAmount(maximumAmount) + ConsoleColor.reset);
                } else {
                    valid = true;
                    message = "";
                }

                // Add the argument result to the list.
                argumentResults.add(new ArgumentResult(valid, NyzoStringEncoder.encode(micropay), message));
            } else {
                String message = argumentValues.get(0).trim().isEmpty() ? "missing Micropay string" :
                        "not a valid Micropay string";
                argumentResults.add(new ArgumentResult(false, argumentValues.get(0), message));
            }

            // Produce the result.
            result = new ValidationResult(argumentResults);

        } catch (Exception ignored) { }

        // If the confirmation result is null, create an exception result. This will only happen if an exception is not
        // handled properly by the confirmation code.
        if (result == null) {
            result = ValidationResult.exceptionResult(getArgumentNames().length);
        }

        return result;
    }

    @Override
    public void run(List<String> argumentValues) {

        // All checks should have happened in validation. For protection, we do not run this command with invalid
        // arguments, but we also do not need to provide user feedback.

        NyzoStringPrivateSeed senderSeed = getSenderSeed();
        long maximumAmount = getMaximumTransactionAmountMicronyzos();

        NyzoString decodedString = NyzoStringEncoder.decode(argumentValues.get(0));
        if (senderSeed != null && decodedString instanceof NyzoStringMicropay) {
            NyzoStringMicropay micropay = (NyzoStringMicropay) decodedString;
            if (micropay.getAmount() <= maximumAmount) {
                ClientTransactionUtil.createAndSendTransaction(senderSeed,
                        new NyzoStringPublicIdentifier(micropay.getReceiverIdentifier()), micropay.getSenderData(),
                        micropay.getAmount(), micropay.getReceiverIpAddress(), micropay.getReceiverPort());
            }
        }
    }

    private static NyzoStringPrivateSeed getSenderSeed() {

        NyzoString micropaySenderSeed  = NyzoStringEncoder.decode(PreferencesUtil.get(micropaySenderSeedKey));
        NyzoStringPrivateSeed senderSeed = null;
        if (micropaySenderSeed instanceof NyzoStringPrivateSeed) {
            senderSeed = ((NyzoStringPrivateSeed) micropaySenderSeed);
        }

        return senderSeed;
    }

    private static long getMaximumTransactionAmountMicronyzos() {
        return (long) (PreferencesUtil.getDouble(micropayMaximumAmountKey, 0) * Transaction.micronyzoMultiplierRatio);
    }
}
