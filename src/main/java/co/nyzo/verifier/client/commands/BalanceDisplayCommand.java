package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.BalanceList;
import co.nyzo.verifier.BalanceListItem;
import co.nyzo.verifier.BalanceListManager;
import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.client.*;
import co.nyzo.verifier.nyzoString.NyzoString;
import co.nyzo.verifier.nyzoString.NyzoStringEncoder;
import co.nyzo.verifier.nyzoString.NyzoStringPublicIdentifier;
import co.nyzo.verifier.util.PrintUtil;

import java.util.*;

public class BalanceDisplayCommand implements Command {

    @Override
    public String getShortCommand() {
        return "BL";
    }

    @Override
    public String getLongCommand() {
        return "balance";
    }

    @Override
    public String getDescription() {
        return "display wallet balances";
    }

    @Override
    public String[] getArgumentNames() {
        return new String[] { "wallet ID or prefix" };
    }

    @Override
    public String[] getArgumentIdentifiers() {
        return new String[] { "walletId" };
    }

    @Override
    public boolean requiresValidation() {
        return false;
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
        return null;
    }

    @Override
    public ExecutionResult run(List<String> argumentValues, CommandOutput output) {

        String walletIdOrPrefix = argumentValues.size() < 1 ? "" : argumentValues.get(0);
        walletIdOrPrefix = walletIdOrPrefix == null ? "" : walletIdOrPrefix.trim().toLowerCase();
        walletIdOrPrefix = walletIdOrPrefix.replaceAll("[^a-f0-9]", "");

        // Make the lists for the notices and errors. Make the result table.
        List<String> notices = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        CommandTable table = new CommandTable(new CommandTableHeader("block height", "blockHeight"),
                new CommandTableHeader("wallet ID", "walletId", true),
                new CommandTableHeader("ID string", "walletIdNyzoString", true),
                new CommandTableHeader("balance", "balance"));

        // Add a notice showing the prefix after normalization.
        notices.add("Wallet ID or prefix after normalization: " + walletIdOrPrefix);

        // Produce the results.
        if (walletIdOrPrefix.isEmpty()) {
            errors.add("You must provide a wallet ID or prefix");
        } else {

            BalanceList balanceList = BalanceListManager.getFrozenEdgeList();
            if (balanceList == null) {
                errors.add("Unable to get balance list");
            } else {
                int numberFound = 0;
                for (BalanceListItem item : balanceList.getItems()) {
                    String identifier = ByteUtil.arrayAsStringNoDashes(item.getIdentifier());
                    if (identifier.startsWith(walletIdOrPrefix)) {
                        numberFound++;
                        NyzoString identifierString = new NyzoStringPublicIdentifier(item.getIdentifier());
                        table.addRow(balanceList.getBlockHeight(),
                                ByteUtil.arrayAsStringWithDashes(item.getIdentifier()),
                                NyzoStringEncoder.encode(identifierString),
                                PrintUtil.printAmount(item.getBalance()));
                    }
                }

                if (numberFound == 0) {
                    notices.add("Unable to find any accounts matching ID/prefix " + walletIdOrPrefix);
                }
            }
        }

        return new SimpleExecutionResult(table, notices, errors);
    }
}
