package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.client.CommandOutput;
import co.nyzo.verifier.client.ExecutionResult;
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
    boolean isLongRunning();
    ValidationResult validate(List<String> argumentValues, CommandOutput output);
    ExecutionResult run(List<String> argumentValues, CommandOutput output);
}
