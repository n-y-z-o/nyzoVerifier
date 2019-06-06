package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.client.ValidationResult;

import java.util.List;

public interface Command {

    String getShortCommand();
    String getLongCommand();
    String getDescription();
    String[] getArgumentNames();
    boolean requiresValidation();
    boolean requiresConfirmation();
    ValidationResult validate(List<String> argumentValues);
    void run(List<String> argumentValues);
}
