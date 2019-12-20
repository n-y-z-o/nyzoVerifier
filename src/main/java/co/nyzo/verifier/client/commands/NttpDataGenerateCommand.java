package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.BlockManager;
import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.KeyUtil;
import co.nyzo.verifier.Transaction;
import co.nyzo.verifier.client.*;
import co.nyzo.verifier.messages.CycleTransactionSignature;
import co.nyzo.verifier.nyzoString.NyzoString;
import co.nyzo.verifier.nyzoString.NyzoStringEncoder;
import co.nyzo.verifier.nyzoString.NyzoStringPrivateSeed;
import co.nyzo.verifier.nyzoString.NyzoStringPublicIdentifier;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.SignatureUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NttpDataGenerateCommand implements Command {

    @Override
    public String getShortCommand() {
        return "NTTP";
    }

    @Override
    public String getLongCommand() {
        return "nttpGenerate";
    }

    @Override
    public String getDescription() {
        return "generate NTTP sender data";
    }

    @Override
    public String[] getArgumentNames() {
        return new String[] { "NTTP number", "Git hash" };
    }

    @Override
    public String[] getArgumentIdentifiers() {
        return new String[] { "nttpNumber", "gitHash" };
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
    public ValidationResult validate(List<String> argumentValues, CommandOutput output) {

        ValidationResult result = null;
        try {
            // Make a list for the argument result items.
            List<ArgumentResult> argumentResults = new ArrayList<>();

            // Check the NTTP number. It must be greater than zero.
            int nttpNumber = 0;
            try {
                nttpNumber = Integer.parseInt(argumentValues.get(0).trim());
            } catch (Exception ignored) { }

            // Ensure that a valid number was provided, limiting the maximum to a reasonable range.
            if (nttpNumber < 1 || nttpNumber > 1_000_000) {
                if (argumentValues.get(0).trim().isEmpty()) {
                    argumentResults.add(new ArgumentResult(false, "", "please provide a number"));
                } else {
                    argumentResults.add(new ArgumentResult(false, argumentValues.get(0).trim(), "invalid number"));
                }
            } else {
                argumentResults.add(new ArgumentResult(true, nttpNumber + ""));
            }

            // Check the hash. It must be a 40-digit (20-byte) string, containing only hexadecimal digits (0-9, a-f).
            char[] hash = argumentValues.get(1).trim().toLowerCase().toCharArray();
            if (hash.length == 40) {
                boolean valid = true;
                for (int i = 0; i < 40 && valid; i++) {
                    valid = (hash[i] >= '0' && hash[i] <= '9') || (hash[i] >= 'a' && hash[i] <= 'f');
                }
                if (valid) {
                    argumentResults.add(new ArgumentResult(true, new String(hash)));
                } else {
                    argumentResults.add(new ArgumentResult(false, argumentValues.get(1).trim(), "not a valid hash"));
                }
            } else {
                String message = hash.length == 0 ? "please provide a hash" :
                        "incorrect length; must be 40 digits (20 bytes)";
                argumentResults.add(new ArgumentResult(false, argumentValues.get(1).trim(), message));
            }

            result = new ValidationResult(argumentResults);

        } catch (Exception ignored) { }

        // If the validation result is null, create an exception result. This will only happen if an exception is not
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
            int nttpNumber = Integer.parseInt(argumentValues.get(0));
            byte[] hashBytes = ByteUtil.byteArrayFromHexString(argumentValues.get(1), 20);

            // Assemble the byte array.
            byte[] labelBytes = ("NTTP-" + nttpNumber + ": ").getBytes(StandardCharsets.UTF_8);
            byte[] result = new byte[labelBytes.length + hashBytes.length];
            System.arraycopy(labelBytes, 0, result, 0, labelBytes.length);
            System.arraycopy(hashBytes, 0, result, labelBytes.length, hashBytes.length);

            System.out.println("label data: " + ByteUtil.arrayAsStringNoDashes(labelBytes));
            System.out.println("hash data: " + ByteUtil.arrayAsStringNoDashes(hashBytes));

            // Display the results.
            ConsoleUtil.printTable(output, "sender data", ClientTransactionUtil.normalizedSenderDataString(result));

            CycleTransactionSendCommand sendCommand = new CycleTransactionSendCommand();
            output.println("Please use this in the sender-data field of the cycle-transaction send command (" +
                    sendCommand.getShortCommand() + "/" + sendCommand.getLongCommand() +
                    ") to initiate a vote for NTTP-" + nttpNumber + " at commit " +
                    ByteUtil.arrayAsStringNoDashes(hashBytes) + ".");
        } catch (Exception ignored) { }
    }
}
