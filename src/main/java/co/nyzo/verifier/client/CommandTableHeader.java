package co.nyzo.verifier.client;

public class CommandTableHeader {

    private String label;
    private String identifier;
    private boolean extraWrapColumn;

    public CommandTableHeader(String label, String identifier) {
        this.label = label;
        this.identifier = identifier;
        this.extraWrapColumn = false;
    }

    public CommandTableHeader(String label, String identifier, boolean extraWrapColumn) {
        this.label = label;
        this.identifier = identifier;
        this.extraWrapColumn = extraWrapColumn;
    }

    public String getLabel() {
        return label;
    }

    public String getIdentifier() {
        return identifier;
    }

    public boolean isExtraWrapColumn() {
        return extraWrapColumn;
    }
}
