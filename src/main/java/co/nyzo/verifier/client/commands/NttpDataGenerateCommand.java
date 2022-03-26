package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.client.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    public boolean isLongRunning() {
        return false;
    }

    @Override
    public ValidationResult validate(List<String> argumentValues, CommandOutput output) {

        ValidationResult result = null;
        try {
            // Make a list for the argument result items.
            List<ArgumentResult> argumentResults = new ArrayList<>();

            // Get the NTTP argument. The cases for simple and compound NTTP numbers are handled separately.
            String nttpArgument = argumentValues.get(0).trim();
            if (nttpArgument.contains("/")) {
                // This is the case for a compound NTTP number. Check both portions.
                String[] split = nttpArgument.split("/");
                int primaryNumber = 0;
                int secondaryNumber = 0;
                if (split.length == 2) {
                    try {
                        primaryNumber = Integer.parseInt(split[0]);
                        secondaryNumber = Integer.parseInt(split[1]);
                    } catch (Exception ignored) { }
                }

                // Limit the primary and secondary numbers to ranges that ensure the compound NTTP number, including the
                // slash, does not exceed 5 characters. NTTP number values less than 1 are not valid.
                if (primaryNumber > 0 && primaryNumber < 1000 && secondaryNumber > 0  && secondaryNumber < 10 &&
                        nttpArgument.equals(primaryNumber + "/" + secondaryNumber)) {
                    argumentResults.add(new ArgumentResult(true, nttpArgument));
                } else {
                    argumentResults.add(new ArgumentResult(false, nttpArgument, "invalid compound NTTP number"));
                }
            } else {
                // This is the case for a simple NTTP number. Check the number. It must be greater than zero.
                int nttpNumber = 0;
                try {
                    nttpNumber = Integer.parseInt(argumentValues.get(0).trim());
                } catch (Exception ignored) { }

                // Limit the NTTP number to a range that occupies no more than 5 characters. NTTP number values less
                // than 1 are not valid.
                if (nttpNumber > 0 && nttpNumber < 100_000) {
                    argumentResults.add(new ArgumentResult(true, nttpNumber + ""));
                } else {
                    if (argumentValues.get(0).trim().isEmpty()) {
                        argumentResults.add(new ArgumentResult(false, "", "please provide a number"));
                    } else {
                        argumentResults.add(new ArgumentResult(false, nttpArgument, "invalid number"));
                    }
                }
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
    public ExecutionResult run(List<String> argumentValues, CommandOutput output) {

        CommandTable table = new CommandTable(new CommandTableHeader("NTTP number", "nttpNumber"),
                new CommandTableHeader("Git hash", "gitHash", true),
                new CommandTableHeader("sender data", "senderData", true));
        table.setInvertedRowsColumns(true);
        List<String> notices = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try {
            // Get the arguments.
            String nttpNumber = argumentValues.get(0).trim();
            byte[] hashBytes = ByteUtil.byteArrayFromHexString(argumentValues.get(1), 20);

            // Assemble the byte array.
            byte[] labelBytes = ("NTTP-" + nttpNumber + ": ").getBytes(StandardCharsets.UTF_8);
            byte[] result = new byte[labelBytes.length + hashBytes.length];
            System.arraycopy(labelBytes, 0, result, 0, labelBytes.length);
            System.arraycopy(hashBytes, 0, result, labelBytes.length, hashBytes.length);

            // Produce the output.
            table.addRow(nttpNumber, ByteUtil.arrayAsStringNoDashes(hashBytes),
                    ClientTransactionUtil.normalizedSenderDataString(result));

            CycleTransactionSendCommand sendCommand = new CycleTransactionSendCommand();
            output.println("Please use this in the sender-data field of the cycle-transaction send command (" +
                    sendCommand.getShortCommand() + "/" + sendCommand.getLongCommand() +
                    ") to initiate a vote for NTTP-" + nttpNumber + " at commit " +
                    ByteUtil.arrayAsStringNoDashes(hashBytes) + ".");
        } catch (Exception e) {
            errors.add("An unexpected error occurred while running this command.");
        }

        return new SimpleExecutionResult(notices, errors, table);
    }
}
