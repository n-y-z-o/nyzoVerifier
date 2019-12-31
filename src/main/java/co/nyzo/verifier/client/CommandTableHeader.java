package co.nyzo.verifier.client;

public class CommandTableHeader {

    private String label;
    private String identifier;

    public CommandTableHeader(String label, String identifier) {
        this.label = label;
        this.identifier = identifier;
    }

    public String getLabel() {
        return label;
    }

    public String getIdentifier() {
        return identifier;
    }
}
