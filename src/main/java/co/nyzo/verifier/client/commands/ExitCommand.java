package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.client.ValidationResult;
import co.nyzo.verifier.client.ConsoleUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.util.Collections;
import java.util.List;

public class ExitCommand implements Command {

    @Override
    public String getShortCommand() {
        return "X";
    }

    @Override
    public String getLongCommand() {
        return "exit";
    }

    @Override
    public String getDescription() {
        return "exit Nyzo client";
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

        String message = "Thank you for using the Nyzo client!";
        ConsoleUtil.printTable(Collections.singletonList(Collections.singletonList(message)));
        UpdateUtil.terminate();
    }
}
