package co.nyzo.verifier.messages.debug;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.BlockVote;
import co.nyzo.verifier.messages.MultilineTextResponse;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class NewVerifierTallyStatusResponse implements MessageObject, MultilineTextResponse {

    private List<String> lines;

    public NewVerifierTallyStatusResponse(Message request) {

        // This is a debug request, so it must be signed by the local verifier.
        if (ByteUtil.arraysAreEqual(request.getSourceNodeIdentifier(), Verifier.getIdentifier())) {

            List<String> lines = new ArrayList<>();
            Map<ByteBuffer, Integer> voteTotals = NewVerifierVoteManager.voteTotals();
            for (ByteBuffer identifier : voteTotals.keySet()) {
                lines.add(ByteUtil.arrayAsStringWithDashes(identifier.array()) + " (" +
                        NicknameManager.get(identifier.array()) + "): " + voteTotals.get(identifier));
            }

            this.lines = lines;
        } else {
            this.lines = Arrays.asList("*** Unauthorized ***");
        }
    }

    public NewVerifierTallyStatusResponse(List<String> lines) {

        this.lines = lines;
    }

    public List<String> getLines() {
        return lines;
    }

    @Override
    public int getByteSize() {

        int byteSize = 1;  // list length
        for (String line : lines) {
            byteSize += FieldByteSize.string(line);
        }

        return byteSize;
    }

    @Override
    public byte[] getBytes() {

        byte[] result = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(result);

        buffer.put((byte) lines.size());
        for (String line : lines) {
            byte[] lineBytes = line.getBytes(StandardCharsets.UTF_8);
            buffer.putShort((short) lineBytes.length);
            buffer.put(lineBytes);
        }

        return result;
    }

    public static NewVerifierTallyStatusResponse fromByteBuffer(ByteBuffer buffer) {

        NewVerifierTallyStatusResponse result = null;

        try {
            int numberOfLines = buffer.get() & 0xff;
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < numberOfLines; i++) {
                short lineByteLength = buffer.getShort();
                byte[] lineBytes = new byte[lineByteLength];
                buffer.get(lineBytes);
                lines.add(new String(lineBytes, StandardCharsets.UTF_8));
            }

            result = new NewVerifierTallyStatusResponse(lines);

        } catch (Exception ignored) { }

        return result;
    }

    @Override
    public String toString() {
        return "[NewVerifierTallyStatusResponse(lines=" + lines.size() + ")]";
    }
}
