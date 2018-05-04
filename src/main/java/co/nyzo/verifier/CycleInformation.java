package co.nyzo.verifier;

public class CycleInformation {

    private int cycleLength;
    private int verifierIndexInCycle;
    private long determinationHeight;

    public CycleInformation(int cycleLength, int verifierIndexInCycle, long determinationHeight) {

        this.cycleLength = cycleLength;
        this.verifierIndexInCycle = verifierIndexInCycle;
        this.determinationHeight = determinationHeight;
    }

    public int getCycleLength() {
        return cycleLength;
    }

    public int getVerifierIndexInCycle() {
        return verifierIndexInCycle;
    }

    public long getDeterminationHeight() {
        return determinationHeight;
    }

    public boolean isNewVerifier() {
        return verifierIndexInCycle < 0;
    }

    @Override
    public String toString() {
        return "[CycleInformation (cycleLength=" + cycleLength + ", verifierIndexInCycle=" +
                verifierIndexInCycle + ", determinationHeight=" + determinationHeight + ")]";
    }
}
