package co.nyzo.verifier.messages;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.Message;
import co.nyzo.verifier.MessageObject;
import co.nyzo.verifier.VerifierPerformanceManager;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VerifierRemovalVote implements MessageObject {

    // This message needs to allow more than one vote to avoid situations where multiple verifiers need to be removed
    // but neither is removed due to inconsistencies in which is perceived as worse.
    public static final int maximumNumberOfVotes = 20;

    private List<byte[]> identifiers;

    public VerifierRemovalVote() {

        this.identifiers = VerifierPerformanceManager.getVerifiersOverThreshold();
    }

    public VerifierRemovalVote(List<byte[]> identifiers) {

        if (identifiers == null) {
            this.identifiers = new ArrayList<>();
        } else {
            this.identifiers = new ArrayList<>(identifiers.subList(0, Math.min(identifiers.size(),
                    maximumNumberOfVotes)));

            // A verifier may attempt to remove another verifier by including multiple removal votes for the same
            // verifier. To protect against this, reject the entire vote list if there are any duplicates.
            Set<ByteBuffer> verifiersInSet = new HashSet<>();
            boolean shouldClearList = false;
            for (byte[] identifier : this.identifiers) {
                ByteBuffer identifierBuffer = ByteBuffer.wrap(identifier);
                if (verifiersInSet.contains(identifierBuffer)) {
                    shouldClearList = true;
                }
                verifiersInSet.add(identifierBuffer);
            }

            if (shouldClearList) {
                this.identifiers = new ArrayList<>();
            }
        }
    }

    public List<byte[]> getIdentifiers() {
        return identifiers;
    }

    @Override
    public int getByteSize() {
        return FieldByteSize.unnamedByte + FieldByteSize.identifier * identifiers.size();
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put((byte) identifiers.size());
        for (byte[] identifier : identifiers) {
            buffer.put(identifier);
        }

        return array;
    }

    public static VerifierRemovalVote fromByteBuffer(ByteBuffer buffer) {

        VerifierRemovalVote result = null;
        try {
            int numberOfIdentifiers = Math.min(buffer.get(), maximumNumberOfVotes);
            List<byte[]> identifiers = new ArrayList<>();
            for (int i = 0; i < numberOfIdentifiers; i++) {
                identifiers.add(Message.getByteArray(buffer, FieldByteSize.identifier));
            }

            result = new VerifierRemovalVote(identifiers);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("[VerifierRemovalVote(" + identifiers.size() + "): {");
        String separator = "";
        for (int i = 0; i < identifiers.size() && i < 5; i++) {
            result.append(separator).append(PrintUtil.superCompactPrintByteArray(identifiers.get(i)));
            separator = ",";
        }
        if (identifiers.size() > 5) {
            result.append("...");
        }
        result.append("}]");

        return result.toString();
    }
}

