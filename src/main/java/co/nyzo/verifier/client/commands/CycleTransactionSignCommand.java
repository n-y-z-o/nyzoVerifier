package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.BlockManager;
import co.nyzo.verifier.CycleTransactionManager;
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
import java.util.Comparator;
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
        return new String[] { "transaction index to sign (from " + listCommand.getLongCommand() + "/" +
                listCommand.getShortCommand() + " command)", "signer key" };
    }

    @Override
    public String[] getArgumentIdentifiers() {
        return new String[] { "transactionIndex", "signerKey" };
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

            // Check if any transactions are available. If not, this command cannot run properly.
            List<Transaction> transactions = CycleTransactionListCommand.getTransactionList();
            int index = -1;
            if (transactions.isEmpty()) {
                CycleTransactionListCommand listCommand = new CycleTransactionListCommand();
                output.println(ConsoleColor.Yellow.background() + "No cycle transactions are available. Please " +
                        "run the " + listCommand.getLongCommand() + " (" + listCommand.getShortCommand() +
                        ") command." + ConsoleColor.reset);
                argumentResults.add(new ArgumentResult(false, "", "no transactions available"));
                argumentResults.add(new ArgumentResult(false, "", "no transactions available"));
            } else {
                // Check the index.
                try {
                    index = Integer.parseInt(argumentValues.get(0));
                } catch (Exception ignored) { }

                if (index < 0 || index > transactions.size()) {
                    argumentResults.add(new ArgumentResult(false, argumentValues.get(0), "invalid index"));
                } else {
                    argumentResults.add(new ArgumentResult(true, index + ""));
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
            }

            result = new ValidationResult(argumentResults);

            // If the result is valid, get the transaction and display its properties.
            Transaction transaction = transactions.get(index);
            NyzoStringPublicIdentifier initiator =
                    new NyzoStringPublicIdentifier(transaction.getSenderIdentifier());
            NyzoStringPublicIdentifier receiver =
                    new NyzoStringPublicIdentifier(transaction.getReceiverIdentifier());
            ConsoleUtil.printTable(Arrays.asList(
                    Arrays.asList("initiator", "receiver", "amount", "block", "# signatures"),
                    Arrays.asList(NyzoStringEncoder.encode(initiator), NyzoStringEncoder.encode(receiver),
                            PrintUtil.printAmount(transaction.getAmount()),
                            BlockManager.heightForTimestamp(transaction.getTimestamp()) + "",
                            (transaction.getCycleSignatures().size() + 1) + "")
            ), output);

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
            int index = Integer.parseInt(argumentValues.get(0));
            NyzoStringPrivateSeed signerSeed = (NyzoStringPrivateSeed) NyzoStringEncoder.decode(argumentValues.get(1));
            Transaction transaction = CycleTransactionListCommand.getTransactionList().get(index);

            // Create the signature and send it to the cycle.
            CycleTransactionSignature signature = new CycleTransactionSignature(transaction.getSenderIdentifier(),
                    KeyUtil.identifierForSeed(signerSeed.getSeed()), SignatureUtil.signBytes(transaction.getBytes(true),
                    signerSeed.getSeed()));
            ClientTransactionUtil.sendCycleTransactionSignature(signature, output);
        } catch (Exception e) {
            output.println(ConsoleColor.Red + "unexpected issue sending cycle transaction signature: " +
                    PrintUtil.printException(e) + ConsoleColor.reset);
        }
    }
}
