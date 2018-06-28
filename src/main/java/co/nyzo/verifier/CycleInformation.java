package co.nyzo.verifier;

public class CycleInformation {

    private int cycleLength;
    private int previousCycleLength;
    private int blockVerifierIndexInCycle;
    private int localVerifierIndexInCycle;

    public CycleInformation(int cycleLength, int previousCycleLength, int blockVerifierIndexInCycle,
                            int localVerifierIndexInCycle) {

        this.cycleLength = cycleLength;
        this.previousCycleLength = previousCycleLength;
        this.blockVerifierIndexInCycle = blockVerifierIndexInCycle;
        this.localVerifierIndexInCycle = localVerifierIndexInCycle;
    }

    public int getCycleLength() {
        return cycleLength;
    }

    public int getPreviousCycleLength() {
        return previousCycleLength;
    }

    public int getBlockVerifierIndexInCycle() {
        return blockVerifierIndexInCycle;
    }

    public int getLocalVerifierIndexInCycle() {
        return localVerifierIndexInCycle;
    }

    public boolean isGenesisCycle() {
        return previousCycleLength == 0 && blockVerifierIndexInCycle < 0;
    }

    public boolean isNewVerifier() {
        return blockVerifierIndexInCycle < 0;
    }

    @Override
    public String toString() {
        return String.format("[CycleInformation (c=%d,p=%d,b=%d,l=%d)]", cycleLength, previousCycleLength,
                blockVerifierIndexInCycle, localVerifierIndexInCycle);
    }
}
