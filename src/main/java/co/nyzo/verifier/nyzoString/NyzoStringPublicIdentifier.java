package co.nyzo.verifier.nyzoString;

public class NyzoStringPublicIdentifier implements NyzoString {

    private byte[] identifier;

    public NyzoStringPublicIdentifier(byte[] identifier) {
        this.identifier = identifier;
    }

    public byte[] getIdentifier() {
        return identifier;
    }

    @Override
    public NyzoStringType getType() {
        return NyzoStringType.PublicIdentifier;
    }

    @Override
    public byte[] getBytes() {
        return identifier;
    }
}
