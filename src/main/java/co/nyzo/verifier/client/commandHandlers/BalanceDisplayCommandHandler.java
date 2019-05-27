package co.nyzo.verifier.client.commandHandlers;

import co.nyzo.verifier.BalanceList;
import co.nyzo.verifier.BalanceListItem;
import co.nyzo.verifier.BalanceListManager;
import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.client.ClientDataManager;
import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.client.ConsoleUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.util.*;

public class BalanceDisplayCommandHandler implements CommandHandler {

    @Override
    public String[] getArgumentNames() {
        return new String[] { "wallet ID or prefix" };
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
                columns.add(new ArrayList<>(Arrays.asList("", "balance")));

                int numberFound = 0;
                for (BalanceListItem item : balanceList.getItems()) {
                    String identifier = ByteUtil.arrayAsStringNoDashes(item.getIdentifier());
                    if (identifier.startsWith(walletIdOrPrefix)) {
                        numberFound++;
                        columns.get(0).add(balanceList.getBlockHeight() + "");
                        columns.get(1).add(ByteUtil.arrayAsStringWithDashes(item.getIdentifier()));
                        columns.get(2).add(PrintUtil.printAmount(item.getBalance()));
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
