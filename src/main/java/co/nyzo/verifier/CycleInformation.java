package co.nyzo.verifier;

public class CycleInformation {

    private int cycleLength;
    private int verifierIndexInCycle;

    public CycleInformation(int cycleLength, int verifierIndexInCycle) {

        this.cycleLength = cycleLength;
        this.verifierIndexInCycle = verifierIndexInCycle;
    }

    public int getCycleLength() {
        return cycleLength;
    }

    public int getVerifierIndexInCycle() {
        return verifierIndexInCycle;
    }

    public boolean isNewVerifier() {
        return verifierIndexInCycle < 0;
    }
}
