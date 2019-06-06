package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.BalanceList;
import co.nyzo.verifier.BalanceListItem;
import co.nyzo.verifier.BalanceListManager;
import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.client.ValidationResult;
import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.client.ConsoleUtil;
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
    public boolean requiresValidation() {
        return false;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ValidationResult validate(List<String> argumentValues) {
        return null;
    }

    @Override
    public void run(List<String> argumentValues) {

        String walletIdOrPrefix = argumentValues.size() < 1 ? "" : argumentValues.get(0);
        walletIdOrPrefix = walletIdOrPrefix == null ? "" : walletIdOrPrefix.trim().toLowerCase();
        walletIdOrPrefix = walletIdOrPrefix.replaceAll("[^a-f0-9]", "");

        System.out.println("wallet ID or prefix after normalization: " + walletIdOrPrefix);

        if (walletIdOrPrefix.isEmpty()) {
            System.out.println(ConsoleColor.Red + "please provide a wallet ID or prefix" + ConsoleColor.reset);
        } else {

            BalanceList balanceList = BalanceListManager.getFrozenEdgeList();
            if (balanceList == null) {
                System.out.println(ConsoleColor.Red + "unable to get balance list" + ConsoleColor.reset);
            } else {
                List<List<String>> columns = new ArrayList<>();
                columns.add(new ArrayList<>(Arrays.asList("block", "height")));
                columns.add(new ArrayList<>(Arrays.asList("", "wallet ID")));
                columns.add(new ArrayList<>(Arrays.asList("", "ID string")));
                columns.add(new ArrayList<>(Arrays.asList("", "balance")));

                int numberFound = 0;
                for (BalanceListItem item : balanceList.getItems()) {
                    String identifier = ByteUtil.arrayAsStringNoDashes(item.getIdentifier());
                    if (identifier.startsWith(walletIdOrPrefix)) {
                        numberFound++;
                        NyzoString identifierString = new NyzoStringPublicIdentifier(item.getIdentifier());
                        columns.get(0).add(balanceList.getBlockHeight() + "");
                        columns.get(1).add(ByteUtil.arrayAsStringWithDashes(item.getIdentifier()));
                        columns.get(2).add(NyzoStringEncoder.encode(identifierString));
                        columns.get(3).add(PrintUtil.printAmount(item.getBalance()));
                    }
                }

                if (numberFound == 0) {
                    ConsoleUtil.printTable("unable to find any accounts matching ID/prefix " + walletIdOrPrefix);
                } else {
                    ConsoleUtil.printTable(columns, new HashSet<>(Collections.singleton(1)));
                }
            }
        }
    }
}
