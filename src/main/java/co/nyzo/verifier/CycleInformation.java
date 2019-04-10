package co.nyzo.verifier;

public class CycleInformation {

    private final long blockHeight;
    private final int maximumCycleLength;
    private final int[] cycleLengths;
    private final boolean newVerifier;
    private final boolean inGenesisCycle;

    public CycleInformation(long blockHeight, int maximumCycleLength, int[] cycleLengths, boolean newVerifier,
                            boolean inGenesisCycle) {

        this.blockHeight = blockHeight;
        this.maximumCycleLength = maximumCycleLength;
        this.cycleLengths = cycleLengths;
        this.newVerifier = newVerifier;
        this.inGenesisCycle = inGenesisCycle;
    }

    public int getCycleLength() {
        return cycleLengths[0];
    }

    public int getMaximumCycleLength() {
        return maximumCycleLength;
    }

    public int getCycleLength(int index) {
        return cycleLengths[index];
    }

    public boolean isNewVerifier() {
        return newVerifier;
    }

    public boolean isInGenesisCycle() {
        return inGenesisCycle;
    }

    public long getWindowStartHeight() {
        return blockHeight - cycleLengths[0] - cycleLengths[1] - cycleLengths[2] + 1;
    }

    public long getDeterminationHeight() {

        // This is the height of the lowest block that affects this cycle information. This will give us the last four
        // cycles plus the first block from the fifth cycle.
        return Math.max(0, blockHeight - cycleLengths[0] - cycleLengths[1] - cycleLengths[2] - cycleLengths[3]);
    }

    @Override
    public String toString() {
        return String.format("[CycleInformation (length=%d,new verifier=%b,Genesis cycle=%b)]", cycleLengths[0],
                newVerifier, inGenesisCycle);
    }
}
