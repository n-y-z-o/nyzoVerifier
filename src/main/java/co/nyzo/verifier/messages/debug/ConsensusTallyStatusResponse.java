package co.nyzo.verifier.messages.debug;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.BlockVote;
import co.nyzo.verifier.messages.MultilineTextResponse;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ConsensusTallyStatusResponse implements MessageObject, MultilineTextResponse {

    private List<String> lines;

    public ConsensusTallyStatusResponse(Message request) {

        // This is a debug request, so it must be signed by the local verifier.
        if (ByteUtil.arraysAreEqual(request.getSourceNodeIdentifier(), Verifier.getIdentifier())) {

            List<String> lines = new ArrayList<>();
            List<Long> heights = BlockVoteManager.getHeights();
            Collections.sort(heights);
            for (Long height : heights) {
                Map<ByteBuffer, BlockVote> votesForHeight = BlockVoteManager.votesForHeight(height);
                if (votesForHeight != null) {
                    for (ByteBuffer identifier : votesForHeight.keySet()) {
                        lines.add(height + ": " + NicknameManager.get(identifier.array()) + ", " +
                            PrintUtil.superCompactPrintByteArray(votesForHeight.get(identifier).getHash()));
                    }
                }
            }

            this.lines = lines;
        } else {
            this.lines = Arrays.asList("*** Unauthorized ***");
        }
    }

    public ConsensusTallyStatusResponse(List<String> lines) {

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

    public static ConsensusTallyStatusResponse fromByteBuffer(ByteBuffer buffer) {

        ConsensusTallyStatusResponse result = null;

        try {
            int numberOfLines = buffer.get() & 0xff;
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < numberOfLines; i++) {
                short lineByteLength = buffer.getShort();
                byte[] lineBytes = new byte[lineByteLength];
                buffer.get(lineBytes);
                lines.add(new String(lineBytes, StandardCharsets.UTF_8));
            }

            result = new ConsensusTallyStatusResponse(lines);

        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("[ConsensusTallyStatusResponse(lines=" + lines.size() + ")]");

        return result.toString();
    }
}
