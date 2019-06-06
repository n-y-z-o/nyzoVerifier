package co.nyzo.verifier.client;

import java.util.ArrayList;
import java.util.List;

public class ValidationResult {

    private List<ArgumentResult> argumentResults;

    public ValidationResult(List<ArgumentResult> argumentResults) {
        this.argumentResults = argumentResults;
    }

    public List<ArgumentResult> getArgumentResults() {
        return argumentResults;
    }

    public int numberOfInvalidArguments() {
        int numberOfInvalidArguments = 0;
        if (argumentResults != null) {
            for (ArgumentResult argumentResult : argumentResults) {
                if (!argumentResult.isValid()) {
                    numberOfInvalidArguments++;
                }
            }
        }

        return numberOfInvalidArguments;
    }

    public List<String> getValidatedArguments() {

        List<String> validatedArguments = new ArrayList<>();
        if (argumentResults != null) {
            for (ArgumentResult argumentResult : argumentResults) {
                validatedArguments.add(argumentResult.getValue());
            }
        }

        return validatedArguments;
    }
}
