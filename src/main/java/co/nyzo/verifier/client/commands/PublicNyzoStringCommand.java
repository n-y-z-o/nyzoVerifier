package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.client.ConsoleUtil;
import co.nyzo.verifier.client.ValidationResult;
import co.nyzo.verifier.nyzoString.NyzoStringEncoder;
import co.nyzo.verifier.nyzoString.NyzoStringPrivateSeed;
import co.nyzo.verifier.nyzoString.NyzoStringPublicIdentifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PublicNyzoStringCommand implements Command {

    @Override
    public String getShortCommand() {
        return "NIS";
    }

    @Override
    public String getLongCommand() {
        return "idString";
    }

    @Override
    public String getDescription() {
        return "create Nyzo strings for a public ID";
    }

    @Override
    public String[] getArgumentNames() {
        return new String[] { "public ID" };
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

        byte[] publicIdentifier = ByteUtil.byteArrayFromHexString(argumentValues.get(0), FieldByteSize.identifier);
        NyzoStringPublicIdentifier publicIdentifierString = new NyzoStringPublicIdentifier(publicIdentifier);

        List<String> labels = Arrays.asList("public ID (raw)", "public ID (Nyzo string)");
        List<String> values = Arrays.asList(ByteUtil.arrayAsStringWithDashes(publicIdentifier),
                NyzoStringEncoder.encode(publicIdentifierString));

        ConsoleUtil.printTable(Arrays.asList(labels, values));

        // If this is not a known identifier in the latest balance list, display a warning.
        BalanceList frozenEdgeList = BalanceListManager.getFrozenEdgeList();
        List<BalanceListItem> balanceListItems = frozenEdgeList.getItems();
        boolean foundInBalanceList = false;
        for (int i = 0; i < balanceListItems.size() && !foundInBalanceList; i++) {
            foundInBalanceList = ByteUtil.arraysAreEqual(balanceListItems.get(i).getIdentifier(), publicIdentifier);
        }
        if (!foundInBalanceList) {
            String color = ConsoleColor.Red.toString();
            String reset = ConsoleColor.reset;
            List<String> warning = Arrays.asList(
                    color + "This account was not found in the balance list at height " +
                            frozenEdgeList.getBlockHeight() + "." + reset,
                    color + "If the ID you provided is incorrect, and you send coins to it," + reset,
                    color + "those coins will likely be unrecoverable. Please ensure that this" + reset,
                    color + "address is valid before sending coins." + reset
            );
            ConsoleUtil.printTable(Collections.singletonList(warning));
        }
    }

    public static void printHexWarning() {
        PublicNyzoStringCommand command = new PublicNyzoStringCommand();
        System.out.println(ConsoleColor.Yellow.background() + "You appear to be using a raw hexadecimal " +
                "public ID. Please convert this to a Nyzo string with the \"" + command.getLongCommand() +
                "\" (" + command.getShortCommand() + ") command." + ConsoleColor.reset);
    }
}
