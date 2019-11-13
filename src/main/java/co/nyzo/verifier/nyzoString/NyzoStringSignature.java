package co.nyzo.verifier.nyzoString;

public class NyzoStringSignature implements NyzoString {

    private byte[] signature;

    public NyzoStringSignature(byte[] signature) {
        this.signature = signature;
    }

    public byte[] getSignature() {
        return signature;
    }

    @Override
    public NyzoStringType getType() {
        return NyzoStringType.Signature;
    }

    @Override
    public byte[] getBytes() {
        return signature;
    }
}
