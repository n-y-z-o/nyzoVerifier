package co.nyzo.verifier.messages.debug;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.MultilineTextResponse;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class VerifierRemovalTallyStatusResponse implements MessageObject, MultilineTextResponse {

    private final List<String> lines;

    public VerifierRemovalTallyStatusResponse(Message request) {

        // This is a debug request, so it must be signed by the local verifier.
        if (ByteUtil.arraysAreEqual(request.getSourceNodeIdentifier(), Verifier.getIdentifier())) {

            List<String> lines = new ArrayList<>();
            Map<ByteBuffer, Integer> voteTotals = VerifierRemovalManager.getVoteCounts();
            for (ByteBuffer identifier : voteTotals.keySet()) {
                lines.add(ByteUtil.arrayAsStringWithDashes(identifier.array()) + " (" +
                        NicknameManager.get(identifier.array()) + "): " + voteTotals.get(identifier));
            }

            this.lines = lines;
        } else {
            this.lines = Collections.singletonList("*** unauthorized ***");
        }
    }

    private VerifierRemovalTallyStatusResponse(List<String> lines) {

        this.lines = lines;
    }

    public List<String> getLines() {
        return lines;
    }

    @Override
    public int getByteSize() {

        int byteSize = FieldByteSize.unnamedShort;  // list length
        for (String line : lines) {
            byteSize += FieldByteSize.string(line);
        }

        return byteSize;
    }

    @Override
    public byte[] getBytes() {

        byte[] result = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(result);

        buffer.putShort((short) lines.size());
        for (String line : lines) {
            byte[] lineBytes = line.getBytes(StandardCharsets.UTF_8);
            buffer.putShort((short) lineBytes.length);
            buffer.put(lineBytes);
        }

        return result;
    }

    public static VerifierRemovalTallyStatusResponse fromByteBuffer(ByteBuffer buffer) {

        VerifierRemovalTallyStatusResponse result = null;

        try {
            int numberOfLines = buffer.getShort() & 0xffff;
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < numberOfLines; i++) {
                short lineByteLength = buffer.getShort();
                byte[] lineBytes = new byte[lineByteLength];
                buffer.get(lineBytes);
                lines.add(new String(lineBytes, StandardCharsets.UTF_8));
            }

            result = new VerifierRemovalTallyStatusResponse(lines);

        } catch (Exception ignored) { }

        return result;
    }

    @Override
    public String toString() {
        return "[VerifierRemovalTallyStatusResponse(lines=" + lines.size() + ")]";
    }
}
