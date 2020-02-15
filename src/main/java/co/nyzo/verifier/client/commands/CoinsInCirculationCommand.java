package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.*;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.web.EndpointResponse;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class CoinsInCirculationCommand implements Command {

    @Override
    public String getShortCommand() {
        return "CC";
    }

    @Override
    public String getLongCommand() {
        return "circulation";
    }

    @Override
    public String getDescription() {
        return "display coins in circulation";
    }

    @Override
    public String[] getArgumentNames() {
        return new String[0];
    }

    @Override
    public String[] getArgumentIdentifiers() {
        return new String[0];
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

        // Make the lists and table for the result.
        List<String> notices = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        CommandTable table = new CommandTable(new CommandTableHeader("block height", "blockHeight"),
                new CommandTableHeader("coins in system", "coinsInSystem"),
                new CommandTableHeader("locked account sum", "lockedAccountSum"),
                new CommandTableHeader("unlock threshold", "unlockThreshold"),
                new CommandTableHeader("unlock transfer sum", "unlockTransferSum"),
                new CommandTableHeader("seed account balance", "seedAccountBalance"),
                new CommandTableHeader("transfer account balance", "transferAccountBalance"),
                new CommandTableHeader("cycle account balance", "cycleAccountBalance"),
                new CommandTableHeader("coins in circulation", "coinsInCirculation"));
        table.setInvertedRowsColumns(true);

        // Make the variable for total circulation. This will be used for the plain-text result.
        AtomicLong totalCirculation = new AtomicLong(0);

        // Get the frozen-edge balance list. Continue only if it is available.
        BalanceList balanceList = BalanceListManager.getFrozenEdgeList();
        if (balanceList == null) {
            errors.add("No balance lists available on this system");
        } else {
            // Make a map of balances from the balance list.
            Map<ByteBuffer, Long> balanceMap = BalanceManager.makeBalanceMap(balanceList);

            // Calculate the sum in locked accounts.
            long sumInLockedAccounts = 0L;
            for (ByteBuffer identifier : balanceMap.keySet()) {
                if (LockedAccountManager.accountIsLocked(identifier)) {
                    sumInLockedAccounts += balanceMap.get(identifier);
                }
            }

            // Calculate how much of the locked accounts has been unlocked.
            long unlockedAmountInLockedAccounts = Math.min(sumInLockedAccounts, balanceList.getUnlockThreshold() -
                    balanceList.getUnlockTransferSum());
            long lockedAmountInLockedAccounts = sumInLockedAccounts - unlockedAmountInLockedAccounts;

            // Get the balances of other accounts not in circulation.
            long seedAccountBalance = balanceMap.getOrDefault(ByteBuffer.wrap(BalanceManager.seedAccountIdentifier),
                    0L);
            long transferAccountBalance = balanceMap.getOrDefault(ByteBuffer.wrap(BalanceListItem.transferIdentifier),
                    0L);
            long cycleAccountBalance = balanceMap.getOrDefault(ByteBuffer.wrap(BalanceListItem.cycleAccountIdentifier),
                    0L);

            // Calculate total circulation.
            totalCirculation.set(Transaction.micronyzosInSystem - lockedAmountInLockedAccounts - seedAccountBalance -
                    transferAccountBalance - cycleAccountBalance);

            // Add cells showing how each line affects the number of coins in circulation.
            table.addRow("", "+", "-", "+", "-", "-", "-", "-", "=");

            // Add the data to the table.
            table.addRow(balanceList.getBlockHeight(),
                    PrintUtil.printAmountWithCommas(Transaction.micronyzosInSystem),
                    PrintUtil.printAmountWithCommas(sumInLockedAccounts),
                    PrintUtil.printAmountWithCommas(balanceList.getUnlockThreshold()),
                    PrintUtil.printAmountWithCommas(balanceList.getUnlockTransferSum()),
                    PrintUtil.printAmountWithCommas(seedAccountBalance),
                    PrintUtil.printAmountWithCommas(transferAccountBalance),
                    PrintUtil.printAmountWithCommas(cycleAccountBalance),
                    PrintUtil.printAmountWithCommas(totalCirculation.get()));
        }

        // For the API result, return the value as a plain text string, in Nyzos, without the symbol. This improves ease
        // of parsing. For console and HTML, provide more context.
        return new SimpleExecutionResult(table, notices, errors) {
            @Override
            public EndpointResponse toEndpointResponse() {
                double totalCirculationNyzos = totalCirculation.get() / (double) Transaction.micronyzoMultiplierRatio;
                byte[] resultBytes = String.format("%6f", totalCirculationNyzos).getBytes(StandardCharsets.UTF_8);
                return new EndpointResponse(resultBytes, EndpointResponse.contentTypeText);
            }
        };
    }
}
