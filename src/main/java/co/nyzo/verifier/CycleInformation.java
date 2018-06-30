package co.nyzo.verifier;

import co.nyzo.verifier.Block.ContinuityState;

public class CycleInformation {

    private int[] cycleLengths;
    private boolean newVerifier;
    private boolean genesisCycle;

    public CycleInformation(int[] cycleLengths, boolean newVerifier, boolean genesisCycle) {

        this.cycleLengths = cycleLengths;
        this.newVerifier = newVerifier;
        this.genesisCycle = genesisCycle;
    }

    public int getCycleLength() {
        return cycleLengths[0];
    }

    public int getCycleLength(int index) {
        return cycleLengths[index];
    }

    public boolean isNewVerifier() {
        return newVerifier;
    }

    public boolean isGenesisCycle() {
        return genesisCycle;
    }

    @Override
    public String toString() {
        return String.format("[CycleInformation (c=%d,n=%d,G=%d)]", cycleLengths[0], newVerifier, genesisCycle);
    }
}
