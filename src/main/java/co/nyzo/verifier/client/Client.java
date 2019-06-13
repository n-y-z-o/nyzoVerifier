package co.nyzo.verifier.client;

import co.nyzo.verifier.BlockManager;
import co.nyzo.verifier.RunMode;
import co.nyzo.verifier.Version;
import co.nyzo.verifier.client.commands.Command;
import co.nyzo.verifier.client.commands.EmptyCommand;
import co.nyzo.verifier.client.commands.InvalidCommand;
import co.nyzo.verifier.util.PreferencesUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.UpdateUtil;
import co.nyzo.verifier.web.WebListener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.List;

public class Client {

    public static void main(String[] args) {

        RunMode.setRunMode(RunMode.Client);

        ConsoleUtil.printTable(Collections.singletonList(Collections.singletonList("Nyzo client, version " +
                Version.getVersion())));

        // Start the data manager. This collects the data necessary for the client to run properly.
        ClientDataManager.start();

        // If the preference is set, start the web listener.
        if (PreferencesUtil.getBoolean(WebListener.startWebListenerKey, false)) {
            WebListener.start();
        }

        runCommandLoop();
    }

    private static void runCommandLoop() {

        // Create a reader for the standard input stream.
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        // Print the commands once before entering the command loop.
        CommandManager.printCommands();

        // Enter the command loop.
        while (!UpdateUtil.shouldTerminate()) {

            // Show the open and frozen edge at the beginning of each command loop.
            printFrozenAndOpenEdges();

            // Get the command and the argument names.
            Command command = readCommand(reader);
            String[] argumentNames = command.getArgumentNames();

            // This loop handles collection of argument values, validation, and confirmation.
            boolean canceled = false;
            boolean completed = false;
            boolean editRequested = false;
            List<String> argumentValues = null;
            ValidationResult validationResult = null;
            while (!canceled && !completed) {

                // This block collects and validates the argument values. If validation is not necessary, collection
                // happens only one. If validation is necessary, collection happens until the values are all valid or
                // the user cancels the command.
                while (!canceled &&
                        (argumentValues == null ||  // collects arguments for all handlers on the first iteration
                                editRequested ||    // collects new arguments when an edit was requested at confirmation
                                (command.requiresValidation() &&  // loops until validation passes
                                        (validationResult == null ||
                                        validationResult.numberOfInvalidArguments() > 0)))) {

                    // If there are invalid arguments, provide the option to cancel.
                    if (validationResult != null && validationResult.numberOfInvalidArguments() > 0) {

                        // Print the arguments, showing which are invalid.
                        List<String> validInvalidColumn = new ArrayList<>();
                        validInvalidColumn.add("valid/");
                        validInvalidColumn.add("invalid");
                        List<String> validationMessageColumn = new ArrayList<>();
                        validationMessageColumn.add("validation");
                        validationMessageColumn.add("message");
                        for (int i = 0; i < argumentNames.length; i++) {
                            ArgumentResult argumentResult = validationResult.getArgumentResults().get(i);
                            if (argumentResult.isValid()) {
                                validInvalidColumn.add("valid");
                                validationMessageColumn.add(argumentResult.getValidationMessage());
                            } else {
                                validInvalidColumn.add(ConsoleColor.Red + "invalid" + ConsoleColor.reset);
                                validationMessageColumn.add(ConsoleColor.Red + argumentResult.getValidationMessage() +
                                        ConsoleColor.reset);
                            }
                        }
                        List<String> argumentNamesWithHeader = new ArrayList<>(Arrays.asList("argument", "name"));
                        argumentNamesWithHeader.addAll(Arrays.asList(argumentNames));

                        List<String> argumentValuesWithHeader = new ArrayList<>(Arrays.asList("argument", "value"));
                        argumentValuesWithHeader.addAll(validationResult.getValidatedArguments());

                        ConsoleUtil.printTable(Arrays.asList(argumentNamesWithHeader, validInvalidColumn,
                                argumentValuesWithHeader, validationMessageColumn),
                                new HashSet<>(Collections.singleton(1)));

                        // Print a notice that the invalid arguments must be corrected.
                        System.out.println(ConsoleColor.Red + "** " +
                                (validationResult.numberOfInvalidArguments() == 1 ? "1 argument" :
                                        validationResult.numberOfInvalidArguments() + " arguments") +
                                " must be corrected to continue **" + ConsoleColor.reset);

                        // Get the response: cancel or edit.
                        String response = "";
                        while (!response.equals("c") && !response.equals("cancel") &&
                                !response.equals("e") && !response.equals("edit")) {
                            System.out.print("cancel (c) or edit (e): ");
                            try {
                                response = reader.readLine().toLowerCase();
                            } catch (Exception ignored) { }
                        }

                        // Process the response.
                        if (response.equals("c") || response.equals("cancel")) {
                            canceled = true;
                        }
                    }

                    // If not canceled, read the arguments.
                    if (!canceled) {
                        argumentValues = readArgumentValues(argumentNames, reader, validationResult);
                        editRequested = false;
                    }

                    // Perform validation on the arguments.
                    if (!canceled && command.requiresValidation()) {

                        // Reload the preferences. Some commands will not validate if correct preferences are not set,
                        // so a user may want to edit the preferences file and attempt validation again.
                        PreferencesUtil.reloadPreferences();

                        validationResult = command.validate(argumentValues);
                    }
                }

                // Only continue if not canceled in the validation block.
                if (!canceled) {

                    // Confirm the command, if necessary.
                    boolean runCommand = false;
                    if (command.requiresConfirmation()) {

                        // Print the argument names and values.
                        List<List<String>> columns = new ArrayList<>();
                        columns.add(Arrays.asList(argumentNames));
                        if (validationResult == null) {
                            columns.add(argumentValues);
                        } else {
                            columns.add(validationResult.getValidatedArguments());
                        }
                        ConsoleUtil.printTable(columns);

                        // Get the response: yes, cancel, or edit.
                        String response = "";
                        while (!response.equals("y") && !response.equals("yes") &&
                                !response.equals("c") && !response.equals("cancel") &&
                                !response.equals("e") && !response.equals("edit")) {
                            System.out.print("run command with these values? yes (y), cancel (c), or edit (e): ");
                            try {
                                response = reader.readLine().toLowerCase();
                            } catch (Exception ignored) { }
                        }

                        // Process the response.
                        if (response.equals("y") || response.equals("yes")) {
                            runCommand = true;
                        } else if (response.equals("c") || response.equals("cancel")) {
                            canceled = true;
                        } else {  // "e" or "edit"
                            editRequested = true;
                        }
                    } else {
                        // If confirmation is not required, the command runs automatically.
                        runCommand = true;
                    }

                    // Run the command, if instructed to do so, and mark completion to break out of the loop for this
                    // command.
                    if (runCommand) {
                        try {
                            command.run(argumentValues);
                        } catch (Exception e) {
                            System.out.println(ConsoleColor.Red + "exception running command: " +
                                    PrintUtil.printException(e) + ConsoleColor.reset);
                        }
                        completed = true;
                    }
                }
            }
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

        Command selectedCommand = null;
        String line = "";
        try {
            line = reader.readLine();
            line = line == null ? "" : line.trim();

            if (line.isEmpty()) {
                selectedCommand = new EmptyCommand();
            } else {
                for (Command command : CommandManager.getCommands()) {
                    if (line.equalsIgnoreCase(command.getShortCommand()) ||
                            line.equalsIgnoreCase(command.getLongCommand())) {
                        selectedCommand = command;
                    }
                }
            }
        } catch (Exception ignored) { }

        if (selectedCommand == null) {
            selectedCommand = new InvalidCommand();
        }

        return selectedCommand;
    }

    private static List<String> readArgumentValues(String[] argumentNames, BufferedReader reader,
                                                   ValidationResult confirmationResult) {

        boolean havePreviousArgumentValues = confirmationResult != null &&
                confirmationResult.getArgumentResults().size() == argumentNames.length;

        List<String> values = new ArrayList<>();
        for (int i = 0; i < argumentNames.length; i++) {

            // Display the argument name. If a previous argument value is provided, display it as well.
            System.out.print(argumentNames[i]);
            if (havePreviousArgumentValues) {
                ArgumentResult argumentResult = confirmationResult.getArgumentResults().get(i);
                if (!argumentResult.getValue().isEmpty()) {
                    ConsoleColor color = argumentResult.isValid() ? ConsoleColor.Green : ConsoleColor.Red;
                    System.out.print(" (" + color + argumentResult.getValue() + ConsoleColor.reset + ")");
                }
            }
            System.out.print(": ");

            // Get the argument value. If a previous argument value is available, and an empty input was provided,
            // use the previous value.
            try {
                String value = reader.readLine();
                value = value == null ? "" : value.trim();
                if (value.isEmpty() && havePreviousArgumentValues) {
                    value = confirmationResult.getArgumentResults().get(i).getValue();
                }
                values.add(value);
            } catch (Exception ignored) { }
        }

        return values;
    }
}
