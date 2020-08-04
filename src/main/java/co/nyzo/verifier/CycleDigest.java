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
    private int[] cycleStartIndices;
    private int[] cycleLengths;
    private int numberOfUniqueIdentifiers;

    private CycleDigest(long blockHeight, List<ByteBuffer> identifiers) {
        this.blockHeight = blockHeight;
        this.identifiers = identifiers;
        this.cycleStartIndices = calculateCycleStartIndices();
        this.cycleLengths = calculateCycleLengths();
        this.numberOfUniqueIdentifiers = new HashSet<>(identifiers).size();

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
        // The cycleStartIndices list and cycleLengths array are derived internally, so they do not need to be checked.
        return Objects.hash(blockHeight, identifiers);
    }

    @Override
    public boolean equals(Object object) {
        // The cycleStartIndices list and cycleLengths array are derived internally, so they do not need to be checked.
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
                isNewVerifier(), isInGenesisCycle());
    }
}
