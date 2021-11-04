package co.nyzo.verifier;

import co.nyzo.verifier.util.LogUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class CycleDigest implements MessageObject {

    // This class is a drop-in replacement for the cycle-information class with one important distinction: the cycle
    // digest for the next block can be derived using only the cycle digest for this block and the verifier identifier
    // of the next block.

    private long blockHeight;
    private List<ByteBuffer> identifiers;
    private NewVerifierState[] newVerifierStates;
    private int[] cycleStartIndices;
    private int[] cycleLengths;
    private int numberOfUniqueIdentifiers;
    private ContinuityState continuityState;

    private CycleDigest(long blockHeight, List<ByteBuffer> identifiers) {
        this.blockHeight = blockHeight;
        this.identifiers = identifiers;
        this.cycleStartIndices = calculateCycleStartIndices();
        this.cycleLengths = calculateCycleLengths();

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

        // Mark whether each identifier is new to the cycle.
        List<ByteBuffer> runningCycle = new ArrayList<>();
        boolean determinedNewVerifier = identifiers.size() == blockHeight + 1;
        newVerifierStates = new NewVerifierState[identifiers.size()];
        for (int i = 0; i < identifiers.size(); i++) {
            ByteBuffer identifier = identifiers.get(i);

            if (runningCycle.contains(identifier)) {
                // This is the case when this is an existing verifier. We have found a complete cycle, which means that
                // we know with certainty whether later verifiers are new.
                determinedNewVerifier = true;
                runningCycle.add(identifier);
                newVerifierStates[i] = NewVerifierState.ExistingVerifier;

                // From the beginning of the cycle, remove all verifiers up to and including the current verifier.
                while (!runningCycle.get(0).equals(identifier)) {
                    runningCycle.remove(0);
                }
                runningCycle.remove(0);
            } else {
                runningCycle.add(identifier);
                newVerifierStates[i] = determinedNewVerifier ? NewVerifierState.NewVerifier :
                        NewVerifierState.Undetermined;
            }
        }
        print(newVerifierStates);

        // Store the number of unique identifiers in the trimmed list.
        this.numberOfUniqueIdentifiers = new HashSet<>(identifiers).size();

        // Calculate the continuity state.
        this.continuityState = calculateContinuityState();
    }

    private void print(NewVerifierState[] states) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < states.length; i++) {
            if (i == cycleStartIndices[0] || i == cycleStartIndices[1] || i == cycleStartIndices[2] ||
                    i == cycleStartIndices[3] || i == cycleStartIndices[4]) {
                stringBuilder.append(' ');
            }
            stringBuilder.append(states[i] == NewVerifierState.Undetermined ? '_' :
                    (states[i] == NewVerifierState.NewVerifier ? 'N' : '-'));
        }
        System.out.println(stringBuilder.toString());
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

    public NewVerifierState getNewVerifierState() {
        return newVerifierStates[newVerifierStates.length - 1];
    }

    public boolean isInGenesisCycle() {
        return cycleStartIndices[0] < 0 && identifiers.size() == blockHeight + 1;
    }

    public long getDeterminationHeight() {
        // This is the height of the lowest block that affects this cycle digest. This will give us the last four
        // cycles plus the first block from the fifth cycle.
        return Math.max(0, blockHeight - cycleLengths[0] - cycleLengths[1] - cycleLengths[2] - cycleLengths[3]);
    }

    public ContinuityState getContinuityState() {
        return continuityState;
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

        // Return a new cycle digest.
        return new CycleDigest(blockHeight, identifiers);
    }

    private int[] calculateCycleStartIndices() {
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

        return cycleStartIndices;
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

    private ContinuityState calculateContinuityState() {

        // Proof-of-diversity rule 1: After the first existing verifier in the block chain, a new verifier is only
        // allowed if none of the other blocks in the cycle, the previous cycle, or the two blocks before the previous
        // cycle were verified by new verifiers.

        // These two variables are set to true as conditions are found that determine them with certainty. If rule1Fail
        // is set to true, this will cause the state to be discontinuous. If undetermined is set to true, this will
        // cause the state to be undetermined.
        boolean rule1Fail = false;
        boolean undetermined = false;

        // Rule 1 is only checked for a new verifier outside the Genesis cycle.
        if (!isInGenesisCycle() && getNewVerifierState() == NewVerifierState.NewVerifier) {
            // This rule can only be checked if the start index of the previous cycle is at least 2.
            if (cycleStartIndices[1] >= 2) {
                for (int i = cycleStartIndices[1] - 2; i < identifiers.size() - 1; i++) {
                    if (newVerifierStates[i] == NewVerifierState.Undetermined) {
                        undetermined = true;
                    } else if (newVerifierStates[i] == NewVerifierState.NewVerifier) {
                        rule1Fail = true;
                    }
                }
            } else {
                undetermined = true;
            }
        } else if (getNewVerifierState() == NewVerifierState.Undetermined) {
            undetermined = true;
        }

        // Depending on the status of rule 1, we may be able to continue to rule 2.
        ContinuityState continuityState;
        if (rule1Fail) {
            continuityState = ContinuityState.Discontinuous;
        } else if (undetermined) {
            continuityState = ContinuityState.Undetermined;
        } else {
            // Proof-of-diversity rule 2: Past the Genesis block, the cycle of a block must be longer than half
            // of one more than the maximum of the all cycle lengths in this cycle and the previous two cycles.

            long threshold = (getMaximumCycleLength() + 1L) / 2L;
            boolean rule2Pass = getBlockHeight() == 0 || getCycleLength() > threshold;
            continuityState = rule2Pass ? ContinuityState.Continuous : ContinuityState.Discontinuous;
        }

        return continuityState;
    }

    @Override
    public int getByteSize() {
        // The serialized representation contains the block height, a 2-byte list length, and the identifiers. An
        // identifier requires 34 bytes the first time it is written: 2 bytes for the short value -1, followed by the
        // 32-byte identifier. For subsequent occurrences of that same identifier, only 2 bytes are required: the 2-byte
        // index references the first occurrence of the identifier, eliminating the need to include the 32-byte
        // representation again.
        return FieldByteSize.blockHeight + FieldByteSize.unnamedShort +
                identifiers.size() * FieldByteSize.unnamedShort +      // all identifiers
                numberOfUniqueIdentifiers * FieldByteSize.identifier;  // first-occurrence identifiers only
    }

    @Override
    public byte[] getBytes() {
        byte[] result = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(result);

        // Add the block height.
        buffer.putLong(blockHeight);

        // Add the identifiers. Add each full identifier only once. For repeat occurrences, add the index in the list of
        // unique identifiers of the previous occurrence.
        buffer.putShort((short) identifiers.size());
        List<ByteBuffer> uniqueIdentifiers = new ArrayList<>();
        for (ByteBuffer identifier : identifiers) {
            int index = uniqueIdentifiers.indexOf(identifier);
            if (index < 0) {
                buffer.putShort((short) -1);
                buffer.put(identifier.array());
                uniqueIdentifiers.add(identifier);
            } else {
                buffer.putShort((short) index);
            }
        }

        return result;
    }

    public static CycleDigest fromByteBuffer(ByteBuffer buffer) {

        CycleDigest result = null;
        try {
            // Get the block height.
            long blockHeight = buffer.getLong();

            // Get the identifiers.
            int numberOfIdentifiers = buffer.getShort();
            List<ByteBuffer> identifiers = new ArrayList<>();
            List<ByteBuffer> uniqueIdentifiers = new ArrayList<>();
            for (int i = 0; i < numberOfIdentifiers; i++) {
                int index = buffer.getShort();
                if (index < 0) {
                    ByteBuffer identifier = ByteBuffer.wrap(Message.getByteArray(buffer, FieldByteSize.identifier));
                    identifiers.add(identifier);
                    uniqueIdentifiers.add(identifier);
                } else {
                    identifiers.add(uniqueIdentifiers.get(index));
                }
            }

            result = new CycleDigest(blockHeight, identifiers);
        } catch (Exception e) {
            LogUtil.println("exception deserializing CycleDigest: " + PrintUtil.printException(e));
        }

        return result;
    }

    @Override
    public int hashCode() {
        // All other fields are derived from blockHeight and identifiers.
        return Objects.hash(blockHeight, identifiers);
    }

    @Override
    public boolean equals(Object object) {
        // All other fields are derived from blockHeight and identifiers.
        boolean result;
        if (this == object) {
            result = true;
        } else if (!(object instanceof CycleDigest)) {
            result = false;
        } else {
            CycleDigest cycleDigest = (CycleDigest) object;
            result = this.blockHeight == cycleDigest.blockHeight && this.identifiers.equals(cycleDigest.identifiers);
        }

        return result;
    }

    @Override
    public String toString() {
        return String.format("[CycleDigest: length=%d, new verifier=%b, Genesis cycle=%b]", getCycleLength(),
                getNewVerifierState(), isInGenesisCycle());
    }
}
