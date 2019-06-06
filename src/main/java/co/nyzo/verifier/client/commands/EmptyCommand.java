package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.client.CommandManager;
import co.nyzo.verifier.client.ValidationResult;

import java.util.*;

public class EmptyCommand implements Command {

    @Override
    public String getShortCommand() {
        return "";
    }

    @Override
    public String getLongCommand() {
        return "empty";
    }

    @Override
    public String getDescription() {
        return "placeholder command handler used when an empty command is provided";
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
    }
}
