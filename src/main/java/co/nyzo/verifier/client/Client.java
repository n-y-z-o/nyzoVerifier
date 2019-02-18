package co.nyzo.verifier.client;

import co.nyzo.verifier.Block;
import co.nyzo.verifier.BlockManager;
import co.nyzo.verifier.Version;
import co.nyzo.verifier.client.commandHandlers.InvalidCommandHandler;
import co.nyzo.verifier.sentinel.ManagedVerifier;
import co.nyzo.verifier.util.UpdateUtil;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

public class Client {

    public static void main(String[] args) {

        ConsoleUtil.printTable(Collections.singletonList(Collections.singletonList("Nyzo client, version " +
                Version.getVersion())));

        // Start the data manager. This collects the data necessary for the client to run properly.
        ClientDataManager.start();


        runCommandLoop();
    }

    private static void runCommandLoop() {

        // Create a reader for the standard input stream.
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        // Print the commands once before entering the command loop.
        InvalidCommandHandler.printCommands();

        // Enter the command loop.
        while (!UpdateUtil.shouldTerminate()) {
            // Show the open and frozen edge at the beginning of each command loop.
            printFrozenAndOpenEdges();

            Command command = readCommand(reader);
            String[] argumentNames = command.getHandler().getArgumentNames();
            List<String> argumentValues = readArgumentValues(argumentNames, reader);
            command.getHandler().run(argumentValues);
        }

        // Close the input stream reader and terminate.
        try {
            reader.close();
        } catch (Exception ignored) { }
    }

    private static void printFrozenAndOpenEdges() {

        System.out.println(ConsoleColor.Blue + "frozen edge: " + BlockManager.getFrozenEdgeHeight() + ", " +
                (BlockManager.openEdgeHeight(false) - BlockManager.getFrozenEdgeHeight()) +
                " from open" + ConsoleColor.reset);
    }

    private static Command readCommand(BufferedReader reader) {

        System.out.print("command: ");

        Command selectedCommand = Command.Invalid;
        String line = "";
        try {
            line = reader.readLine();
            line = line == null ? "" : line.trim();

            for (Command command : Command.values()) {
                if (line.equalsIgnoreCase(command.getShortCommand()) ||
                        line.equalsIgnoreCase(command.getFullCommand())) {
                    selectedCommand = command;
                }
            }
        } catch (Exception ignored) { }

        return selectedCommand;
    }

    private static List<String> readArgumentValues(String[] argumentNames, BufferedReader reader) {

        List<String> values = new ArrayList<>();
        for (String argumentName : argumentNames) {

            System.out.print(argumentName + ": ");
            try {
                String value = reader.readLine();
                value = value == null ? "" : value.trim();
                values.add(value);
            } catch (Exception ignored) { }
        }

        return values;
    }
}
