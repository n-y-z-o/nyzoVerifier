package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.client.CommandManager;
import co.nyzo.verifier.client.ValidationResult;
import co.nyzo.verifier.client.ConsoleColor;

import java.util.*;

public class InvalidCommand implements Command {

    @Override
    public String getShortCommand() {
        return "I";
    }

    @Override
    public String getLongCommand() {
        return "invalid";
    }

    @Override
    public String getDescription() {
        return "an invalid message was provided";
    }


    @Override
    public String[] getArgumentNames() {
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
    public ValidationResult validate(List<String> argumentValues) {
        return null;
    }

    @Override
    public void run(List<String> argumentValues) {

        CommandManager.printCommands();

        System.out.println(ConsoleColor.Red + "Your selection was not recognized." + ConsoleColor.reset);
        System.out.println(ConsoleColor.Red + "Please choose an option from the above commands." + ConsoleColor.reset);
        System.out.println(ConsoleColor.Red + "You may type either the short command or the full command." +
                ConsoleColor.reset);
    }
}
