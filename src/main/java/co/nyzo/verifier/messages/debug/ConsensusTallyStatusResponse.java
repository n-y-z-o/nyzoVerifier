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

            // The BlockVoteManager can store votes for several heights, but the cycle only works on one height at a
            // time, so this response only shows the height directly past the frozen edge.
            List<String> lines = new ArrayList<>();
            long height = BlockManager.getFrozenEdgeHeight() + 1L;

            lines.add("votes for height: " + height);
            Map<ByteBuffer, BlockVote> votesForHeight = BlockVoteManager.votesForHeight(height);
            if (votesForHeight != null && !votesForHeight.isEmpty()) {

                Map<ByteBuffer, Integer> hashCounts = new HashMap<>();
                for (ByteBuffer identifier : votesForHeight.keySet()) {
                    byte[] hash = votesForHeight.get(identifier).getHash();
                    lines.add(NicknameManager.get(identifier.array()) + ", " + PrintUtil.compactPrintByteArray(hash));

                    ByteBuffer hashBuffer = ByteBuffer.wrap(hash);
                    hashCounts.put(hashBuffer, hashCounts.getOrDefault(hashBuffer, 0) + 1);
                }

                lines.add("");
                for (ByteBuffer hash : hashCounts.keySet()) {
                    lines.add(ByteUtil.arrayAsStringWithDashes(hash.array()) + ": " + hashCounts.get(hash));
                }
            } else {
                lines.add("*** no votes available for height " + height + " ***");
            }

            this.lines = lines;
        } else {
            this.lines = Arrays.asList("*** unauthorized ***");
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

        int byteSize = 2;  // list length
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

    public static ConsensusTallyStatusResponse fromByteBuffer(ByteBuffer buffer) {

        ConsensusTallyStatusResponse result = null;

        try {
            int numberOfLines = buffer.getShort() & 0xffff;
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < numberOfLines; i++) {
                int lineByteLength = buffer.getShort() & 0xffff;
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
