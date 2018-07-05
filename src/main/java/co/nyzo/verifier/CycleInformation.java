package co.nyzo.verifier;

import co.nyzo.verifier.Block.ContinuityState;

public class CycleInformation {

    private long blockHeight;
    private int[] cycleLengths;
    private boolean newVerifier;
    private boolean genesisCycle;

    public CycleInformation(long blockHeight, int[] cycleLengths, boolean newVerifier, boolean genesisCycle) {

        this.blockHeight = blockHeight;
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

    public long getWindowStartHeight() {

        // This is the height of the lowest block that affects this cycle information. This will give us the last four
        // cycles plus the first block from the fifth cycle.
        return Math.max(0, blockHeight - cycleLengths[0] - cycleLengths[1] - cycleLengths[2] - cycleLengths[3]);
    }

    @Override
    public String toString() {
        return String.format("[CycleInformation (c=%d,n=%b,G=%b)]", cycleLengths[0], newVerifier, genesisCycle);
    }
}
