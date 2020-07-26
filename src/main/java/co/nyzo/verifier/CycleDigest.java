package co.nyzo.verifier;

import java.nio.ByteBuffer;
import java.util.*;

public class CycleDigest {

    // This class is a drop-in replacement for the cycle-information class with one important distinction: the cycle
    // digest for the next block can be derived using only the cycle digest for this block and the verifier identifier
    // of the next block.

    private long blockHeight;
    private List<ByteBuffer> identifiers;
    private int[] cycleStartIndices;
    private int[] cycleLengths;

    private CycleDigest(long blockHeight, List<ByteBuffer> identifiers, int[] cycleStartIndices) {
        this.blockHeight = blockHeight;
        this.identifiers = identifiers;
        this.cycleStartIndices = cycleStartIndices;
        this.cycleLengths = calculateCycleLengths();
    }

    public long getBlockHeight() {
        return blockHeight;
    }

    public List<ByteBuffer> getIdentifiers() {
        return identifiers;
    }

    public int getCycleLength() {
        return cycleLengths[0];
    }

    public int getMaximumCycleLength() {
        return Math.max(cycleLengths[0], Math.max(cycleLengths[1], cycleLengths[2]));
    }

    public int getCycleLength(int index) {
        return cycleLengths[index];
    }

    public boolean isNewVerifier() {
        return cycleStartIndices[0] < 0 || !identifiers.get(cycleStartIndices[0] - 1)
                .equals(identifiers.get(identifiers.size() - 1));
    }

    public boolean isInGenesisCycle() {
        return cycleStartIndices[0] < 0 && identifiers.size() == blockHeight + 1;
    }

    public long getDeterminationHeight() {
        // This is the height of the lowest block that affects this cycle digest. This will give us the last four
        // cycles plus the first block from the fifth cycle.
        return Math.max(0, blockHeight - cycleLengths[0] - cycleLengths[1] - cycleLengths[2] - cycleLengths[3]);
    }

    public static CycleDigest digestForNextBlock(CycleDigest previousDigest, byte[] nextVerifierIdentifier) {

        // Get the information from the previous digest.
        long blockHeight;
        List<ByteBuffer> identifiers;
        if (previousDigest == null) {
            blockHeight = 0;
            identifiers = new ArrayList<>();
        } else {
            blockHeight = previousDigest.getBlockHeight() + 1L;
            identifiers = new ArrayList<>(previousDigest.getIdentifiers());
        }

        // Add the identifier of the new verifier.
        ByteBuffer nextIdentifierBuffer = ByteBuffer.wrap(nextVerifierIdentifier);
        identifiers.add(nextIdentifierBuffer);

        // Determine the cycle start indices.
        int cycleCount = 0;
        Set<ByteBuffer> currentCycle = new HashSet<>();
        int[] cycleStartIndices = { -1, -1, -1, -1, -1 };
        for (int i = identifiers.size() - 1; i >= 0 && cycleCount < cycleStartIndices.length; i--) {
            ByteBuffer identifier = identifiers.get(i);
            if (currentCycle.contains(identifier)) {
                cycleStartIndices[cycleCount] = i + 1;
                cycleCount++;
                currentCycle.clear();
            }

            currentCycle.add(identifier);
        }

        // If the last cycle-start index is above 1, remove the excess identifiers.
        int indexOffset = cycleStartIndices[cycleStartIndices.length - 1];
        if (indexOffset > 1) {
            for (int i = 0; i < indexOffset - 1; i++) {
                identifiers.remove(0);
            }
            for (int i = 0; i < cycleStartIndices.length; i++) {
                cycleStartIndices[i] -= indexOffset - 1;
            }
        }

        // Return a new cycle digest.
        return new CycleDigest(blockHeight, identifiers, cycleStartIndices);
    }

    private int[] calculateCycleLengths() {
        int[] cycleLengths = new int[cycleStartIndices.length - 1];
        cycleLengths[0] = cycleStartIndices[0] > 0 ? identifiers.size() - cycleStartIndices[0] : identifiers.size();
        int remainingLength = identifiers.size() - cycleLengths[0];
        for (int i = 1; i < cycleLengths.length; i++) {
            if (cycleStartIndices[i] > 0) {
                cycleLengths[i] = cycleStartIndices[i - 1] - cycleStartIndices[i];
            } else if (remainingLength > 0) {
                cycleLengths[i] = remainingLength;
            } else {
                cycleLengths[i] = 0;
            }

            remainingLength -= cycleLengths[i];
        }

        return cycleLengths;
    }

    @Override
    public String toString() {
        return String.format("[CycleDigest: length=%d, new verifier=%b, Genesis cycle=%b]", getCycleLength(),
                isNewVerifier(), isInGenesisCycle());
    }
}
