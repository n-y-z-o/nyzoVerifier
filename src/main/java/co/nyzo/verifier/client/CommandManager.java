package co.nyzo.verifier.client;

import co.nyzo.verifier.client.commands.*;

import java.util.*;

public class CommandManager {

    private static final Command[] commands = {
            new BalanceDisplayCommand(),
            new TransactionSendCommand(),
            new PrivateNyzoStringCommand(),
            new PublicNyzoStringCommand(),
            new PrefilledDataCreateCommand(),
            new PrefilledDataSendCommand(),
            new ExitCommand()
    };

    public static Command[] getCommands() {
        return commands;
    }

    public static void printCommands() {

        // Build the columns.
        List<List<String>> columns = new ArrayList<>();
        columns.add(new ArrayList<>(Arrays.asList("short", "command")));
        columns.add(new ArrayList<>(Arrays.asList("full", "command")));
        columns.add(new ArrayList<>(Arrays.asList("", "description")));
        for (Command command : commands) {
            columns.get(0).add(command.getShortCommand());
            columns.get(1).add(command.getLongCommand());
            columns.get(2).add(command.getDescription());
        }

        // Print the table.
        ConsoleUtil.printTable(columns, new HashSet<>(Collections.singleton(1)));
    }
}
