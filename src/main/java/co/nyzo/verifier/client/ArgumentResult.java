package co.nyzo.verifier.client;

public class ArgumentResult {

    private boolean valid;
    private String value;
    private String validationMessage;

    public ArgumentResult(boolean valid, String value) {
        this.valid = valid;
        this.value = value;
        this.validationMessage = "";
    }

    public ArgumentResult(boolean valid, String value, String validationMessage) {
        this.valid = valid;
        this.value = value;
        this.validationMessage = validationMessage == null ? "" : validationMessage;
    }

    public boolean isValid() {
        return valid;
    }

    public String getValue() {
        return value;
    }

    public String getValidationMessage() {
        return validationMessage;
    }
}
