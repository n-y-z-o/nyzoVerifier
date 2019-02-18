package co.nyzo.verifier.client.commandHandlers;

import co.nyzo.verifier.client.Command;
import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.client.ConsoleUtil;

import java.util.*;

public class InvalidCommandHandler implements CommandHandler {
    @Override
    public String[] getArgumentNames() {
        return new String[0];
    }

    @Override
    public void run(List<String> argumentValues) {

        printCommands();
        System.err.println("Your selection was not recognized.");
        System.err.println("Please choose an option from the above commands.");
        System.err.println("You may type either the short command or the full command.");
    }

    public static void printCommands() {

        // Build the columns.
        List<List<String>> columns = new ArrayList<>();
        columns.add(new ArrayList<>(Arrays.asList("short", "command")));
        columns.add(new ArrayList<>(Arrays.asList("full", "command")));
        columns.add(new ArrayList<>(Arrays.asList("", "description")));
        for (Command command : Command.values()) {
            if (command != Command.Invalid) {
                columns.get(0).add(command.getShortCommand());
                columns.get(1).add(command.getFullCommand());
                columns.get(2).add(command.getDescription());
            }
        }

        // Print the table.
        ConsoleUtil.printTable(columns, new HashSet<>(Collections.singleton(1)));
    }
}
