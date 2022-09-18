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

    private static final int maximumAccountsInResponse = 100;

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

        // Normalize for a raw ID.
        String rawIdOrPrefix = walletIdOrPrefix.replaceAll("[^a-f0-9]", "");

        // Normalize for a Nyzo string.
        String nyzoStringIdOrPrefix = walletIdOrPrefix.toLowerCase();

        // Make the lists for the notices and errors. Make the result table.
        List<String> notices = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        CommandTable table = new CommandTable(new CommandTableHeader("block height", "blockHeight"),
                new CommandTableHeader("wallet ID", "walletId", true),
                new CommandTableHeader("ID string", "walletIdNyzoString", true),
                new CommandTableHeader("balance", "balance"));

        // Determine whether the search is a Nyzo string.
        boolean searchIsNyzoString = nyzoStringIdOrPrefix.startsWith("id__");

        // Add a notice showing the prefix after normalization.
        notices.add("wallet ID or prefix after normalization: " + (searchIsNyzoString ? nyzoStringIdOrPrefix :
                rawIdOrPrefix));

        // Add a notice about what type of search is being performed.
        notices.add("search type: " + (searchIsNyzoString ? "Nyzo string" : "raw ID"));

        // Produce the results.
        if (walletIdOrPrefix.isEmpty()) {
            errors.add("You must provide a wallet ID or prefix");
        } else {

            BalanceList balanceList = BalanceListManager.getFrozenEdgeList();
            if (balanceList == null) {
                errors.add("Unable to get balance list");
            } else {
                int numberFound = 0;
                List<BalanceListItem> items = balanceList.getItems();
                for (int i = 0; i < items.size() && numberFound < maximumAccountsInResponse; i++) {
                    BalanceListItem item = items.get(i);
                    String identifier = ByteUtil.arrayAsStringNoDashes(item.getIdentifier());
                    String identifierString =
                            NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(item.getIdentifier()));
                    if ((searchIsNyzoString && identifierString.startsWith(nyzoStringIdOrPrefix)) ||
                            (!searchIsNyzoString && identifier.startsWith(rawIdOrPrefix))) {
                        numberFound++;
                        table.addRow(balanceList.getBlockHeight(),
                                ByteUtil.arrayAsStringWithDashes(item.getIdentifier()),
                                identifierString,
                                PrintUtil.printAmount(item.getBalance()));
                    }
                }

                if (numberFound == 0) {
                    notices.add("unable to find any accounts matching ID/prefix " + (searchIsNyzoString ?
                            nyzoStringIdOrPrefix : rawIdOrPrefix));
                } else if (numberFound == maximumAccountsInResponse) {
                    notices.add("search is returning maximum number of results (" + maximumAccountsInResponse +
                            "); more results may be available");
                }
            }
        }

        return new SimpleExecutionResult(notices, errors, table);
    }
}
