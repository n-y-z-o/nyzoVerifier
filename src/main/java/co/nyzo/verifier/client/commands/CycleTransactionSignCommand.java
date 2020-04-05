package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.Transaction;
import co.nyzo.verifier.client.*;
import co.nyzo.verifier.nyzoString.*;
import co.nyzo.verifier.util.PrintUtil;

import java.util.ArrayList;
import java.util.List;

public class CycleTransactionSignCommand implements Command {

    @Override
    public String getShortCommand() {
        return "CTX";
    }

    @Override
    public String getLongCommand() {
        return "cycleSign";
    }

    @Override
    public String getDescription() {
        return "sign a cycle transaction";
    }

    @Override
    public String[] getArgumentNames() {
        CycleTransactionListCommand listCommand = new CycleTransactionListCommand();
        return new String[] { "transaction signature", "signer key", "vote (1=yes, 0=no)" };
    }

    @Override
    public String[] getArgumentIdentifiers() {
        return new String[] { "transactionSignature", "signerKey", "vote" };
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

            // Check the transaction signature.
            NyzoString transactionSignature = NyzoStringEncoder.decode(argumentValues.get(0));
            if (transactionSignature instanceof NyzoStringSignature) {
                argumentResults.add(new ArgumentResult(true, NyzoStringEncoder.encode(transactionSignature)));
            } else {
                String message = argumentValues.get(0).trim().isEmpty() ? "missing Nyzo string transaction signature" :
                        "not a valid Nyzo string transaction signature";
                argumentResults.add(new ArgumentResult(false, argumentValues.get(0), message));
            }

            // Check the signer key.
            NyzoString signerSeed = NyzoStringEncoder.decode(argumentValues.get(1));
            if (signerSeed instanceof NyzoStringPrivateSeed) {
                argumentResults.add(new ArgumentResult(true, NyzoStringEncoder.encode(signerSeed)));
            } else {
                String message = argumentValues.get(1).trim().isEmpty() ? "missing Nyzo string private key" :
                        "not a valid Nyzo string private key";
                argumentResults.add(new ArgumentResult(false, argumentValues.get(1), message));

                if (argumentValues.get(1).length() >= 64) {
                    PrivateNyzoStringCommand.printHexWarning(output);
                }
            }

            // Check the vote. It must be either 1 or 0.
            String voteString = argumentValues.get(2);
            if (voteString.equals("0") || voteString.equals("1")) {
                argumentResults.add(new ArgumentResult(true, voteString));
            } else {
                argumentResults.add(new ArgumentResult(false, voteString, "only 1 and 0 are valid vote values"));
            }

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
            NyzoStringSignature transactionSignature =
                    (NyzoStringSignature) NyzoStringEncoder.decode(argumentValues.get(0));
            NyzoStringPrivateSeed signerSeed = (NyzoStringPrivateSeed) NyzoStringEncoder.decode(argumentValues.get(1));
            byte vote = argumentValues.get(2).equals("1") ? (byte) 1 : (byte) 0;

            // Create the signature transaction and send to likely verifiers.
            long timestamp = ClientTransactionUtil.suggestedTransactionTimestamp();
            Transaction transaction = Transaction.cycleSignatureTransaction(timestamp, vote,
                    transactionSignature.getSignature(), signerSeed.getSeed());
            ClientTransactionUtil.sendTransactionToLikelyBlockVerifiers(transaction, true, output);

        } catch (Exception e) {
            output.println(ConsoleColor.Red + "unexpected issue sending cycle-signature transaction: " +
                    PrintUtil.printException(e) + ConsoleColor.reset);
        }

        // ExecutionResult objects are not yet implemented for long-running commands.
        return null;
    }
}
