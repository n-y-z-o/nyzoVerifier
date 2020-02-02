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
            new CycleTransactionSendCommand(),
            new CycleTransactionListCommand(),
            new CycleTransactionSignCommand(),
            new NttpDataGenerateCommand(),
            new TransactionSearchCommand(),
            new CoinsInCirculationCommand(),
            new ExitCommand()
    };

    public static Command[] getCommands() {
        return commands;
    }

    public static void printCommands(CommandOutput output) {

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
        ConsoleUtil.printTable(columns, new HashSet<>(Collections.singleton(1)), output);
    }

    public static void checkCommandStrings() {

        // This is a simple check to ensure no ambiguity in the command strings.
        Set<String> commandStrings = new HashSet<>();
        for (Command command : commands) {
            // Check the short command.
            if (commandStrings.contains(command.getShortCommand())) {
                System.out.println(ConsoleColor.Red.backgroundBright() + "duplicate command string: " +
                        command.getShortCommand() + ConsoleColor.reset);
            } else {
                commandStrings.add(command.getShortCommand());
            }

            // Check the long command.
            if (commandStrings.contains(command.getLongCommand())) {
                System.out.println(ConsoleColor.Red.backgroundBright() + "duplicate command string: " +
                        command.getLongCommand() + ConsoleColor.reset);
            } else {
                commandStrings.add(command.getLongCommand());
            }
        }
    }
}
