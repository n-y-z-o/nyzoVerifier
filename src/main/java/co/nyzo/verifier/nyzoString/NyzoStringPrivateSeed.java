package co.nyzo.verifier.nyzoString;

public class NyzoStringPrivateSeed implements NyzoString {

    private byte[] seed;

    public NyzoStringPrivateSeed(byte[] seed) {
        this.seed = seed;
    }

    public byte[] getSeed() {
        return seed;
    }

    @Override
    public NyzoStringType getType() {
        return NyzoStringType.PrivateSeed;
    }

    @Override
    public byte[] getBytes() {
        return seed;
    }
}
