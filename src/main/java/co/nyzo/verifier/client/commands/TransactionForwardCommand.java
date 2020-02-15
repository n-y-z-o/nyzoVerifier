package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.BlockManager;
import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.Transaction;
import co.nyzo.verifier.client.*;
import co.nyzo.verifier.nyzoString.*;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class TransactionForwardCommand implements Command {

    @Override
    public String getShortCommand() {
        return "TF";
    }

    @Override
    public String getLongCommand() {
        return "forwardTransaction";
    }

    @Override
    public String getDescription() {
        return "forward a transaction";
    }

    @Override
    public String[] getArgumentNames() {
        return new String[] { "transaction (Nyzo string)" };
    }

    @Override
    public String[] getArgumentIdentifiers() {
        return new String[] { "transaction" };
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

            // Check the transaction.
            NyzoString transaction = NyzoStringEncoder.decode(argumentValues.get(0));
            if (transaction instanceof NyzoStringTransaction) {
                argumentResults.add(new ArgumentResult(true, NyzoStringEncoder.encode(transaction)));
            } else {
                String message = argumentValues.get(0).trim().isEmpty() ? "missing Nyzo string transaction" :
                        "not a valid Nyzo string transaction";
                argumentResults.add(new ArgumentResult(false, argumentValues.get(0), message));
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

        // Make the lists for the notices and errors. Make the result table.
        List<String> notices = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        CommandTable table = new CommandTable(new CommandTableHeader("block height", "blockHeight"),
                new CommandTableHeader("sender ID (raw)", "senderIdBytes", true),
                new CommandTableHeader("sender ID (Nyzo string)", "senderIdNyzoString", true),
                new CommandTableHeader("receiver ID (raw)", "receiverIdBytes", true),
                new CommandTableHeader("receiver ID (Nyzo string)", "receiverIdNyzoString", true),
                new CommandTableHeader("amount", "amount"),
                new CommandTableHeader("previous verifier ID (raw)", "previousVerifierIdBytes", true),
                new CommandTableHeader("previous verifier ID (Nyzo string)", "previousVerifierIdNyzoString", true),
                new CommandTableHeader("expected verifier ID (raw)", "expectedVerifierIdBytes", true),
                new CommandTableHeader("expected verifier ID (Nyzo string)", "expectedVerifierIdNyzoString", true),
                new CommandTableHeader("next verifier ID (raw)", "nextVerifierIdBytes", true),
                new CommandTableHeader("next verifier ID (Nyzo string)", "nextVerifierIdNyzoString", true),
                new CommandTableHeader("forwarded", "forwarded"));

        try {
            // Get the transaction.
            Transaction transaction =
                    ((NyzoStringTransaction) NyzoStringEncoder.decode(argumentValues.get(0))).getTransaction();

            // Perform initial validation on the transaction.
            StringBuilder validationError = new StringBuilder();
            StringBuilder validationWarning = new StringBuilder();
            boolean valid = transaction.performInitialValidation(validationError, validationWarning);
            if (validationError.length() > 0) {
                errors.add(validationError.toString().trim());
            }
            if (validationWarning.length() > 0) {
                notices.add(validationWarning.toString().trim());
            }

            // If valid, send the transaction to the cycle.
            ByteBuffer[] verifiers;
            if (valid) {
                verifiers = ClientTransactionUtil.sendTransactionToLikelyBlockVerifiers(transaction, false, output);
            } else {
                verifiers = new ByteBuffer[3];
            }

            // Build the result.
            table.addRow(BlockManager.heightForTimestamp(transaction.getTimestamp()),
                    ByteUtil.arrayAsStringWithDashes(transaction.getSenderIdentifier()),
                    NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(transaction.getSenderIdentifier())),
                    ByteUtil.arrayAsStringWithDashes(transaction.getReceiverIdentifier()),
                    NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(transaction.getReceiverIdentifier())),
                    PrintUtil.printAmount(transaction.getAmount()),
                    verifiers[0] == null ? null : ByteUtil.arrayAsStringWithDashes(verifiers[0].array()),
                    verifiers[0] == null ? null :
                            NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(verifiers[0].array())),
                    verifiers[1] == null ? null : ByteUtil.arrayAsStringWithDashes(verifiers[1].array()),
                    verifiers[1] == null ? null :
                            NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(verifiers[1].array())),
                    verifiers[2] == null ? null : ByteUtil.arrayAsStringWithDashes(verifiers[2].array()),
                    verifiers[2] == null ? null :
                            NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(verifiers[2].array())),
                    valid);

        } catch (Exception e) {
            errors.add("Unexpected issue forwarding transaction: " + PrintUtil.printException(e));
        }

        return new SimpleExecutionResult(table, notices, errors);
    }
}
