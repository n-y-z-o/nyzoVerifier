package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.client.CommandOutput;
import co.nyzo.verifier.client.ValidationResult;

import java.util.List;

public interface Command {

    String getShortCommand();
    String getLongCommand();
    String getDescription();
    String[] getArgumentNames();
    String[] getArgumentIdentifiers();
    boolean requiresValidation();
    boolean requiresConfirmation();
    ValidationResult validate(List<String> argumentValues, CommandOutput output);
    void run(List<String> argumentValues, CommandOutput output);
}
