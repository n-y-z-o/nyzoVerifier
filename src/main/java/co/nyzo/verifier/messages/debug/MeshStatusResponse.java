package co.nyzo.verifier.messages.debug;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.MultilineTextResponse;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MeshStatusResponse implements MessageObject, MultilineTextResponse {

    private static final int maximumNumberOfLines = 2000;

    private final List<String> lines;

    public MeshStatusResponse(Message request) {

        // This is a debug request, so it must be signed by the local verifier.
        if (ByteUtil.arraysAreEqual(request.getSourceNodeIdentifier(), Verifier.getIdentifier())) {

            List<Node> nodes = NodeManager.getMesh();
            nodes.sort((node1, node2) -> {
                Long queueTimestamp1 = node1.getQueueTimestamp();
                Long queueTimestamp2 = node2.getQueueTimestamp();
                return queueTimestamp1.compareTo(queueTimestamp2);
            });

            // TODO: timestamp in-cycle verifiers with current timestamp to prevent promotion to top of queue when
            // TODO:   dropping out
            // TODO: persist join timestamps to file -- this should be done based on identifier, and they should be
            // TODO:   reloaded when the verifier is restarted

            ByteBuffer currentNewVerifierVote = NewVerifierQueueManager.getCurrentVote();
            List<ByteBuffer> topVerifiers = NewVerifierVoteManager.topVerifiers();

            List<String> lines = new ArrayList<>();
            for (int i = 0; i < nodes.size() && lines.size() < maximumNumberOfLines; i++) {
                Node node = nodes.get(i);
                byte[] identifier = node.getIdentifier();
                int topVerifierIndex = topVerifiers.indexOf(ByteBuffer.wrap(identifier));
                boolean isCurrentVote = ByteBuffer.wrap(identifier).equals(currentNewVerifierVote);

                lines.add((BlockManager.verifierInCurrentCycle(ByteBuffer.wrap(identifier)) ? "C, " : " , ") +
                        PrintUtil.compactPrintByteArray(identifier) + ", " + node.getQueueTimestamp() + ", " +
                        (topVerifierIndex < 0 ? "-" : topVerifierIndex + "") + ", " + (isCurrentVote ? "*" : "-") +
                        ", " + NicknameManager.get(identifier));
            }

            this.lines = lines;
        } else {
            this.lines = new ArrayList<>();
        }
    }

    private MeshStatusResponse(List<String> lines) {

        this.lines = lines;
    }

    public List<String> getLines() {
        return lines;
    }

    @Override
    public int getByteSize() {

        // The list length was previously a single byte. If working with an older version of the verifier, this class
        // will not work without modifications. Typically, a new message would be made to handle this sort of change,
        // but as this is a debug message that is rarely used, we felt that adding another message would be wasteful.
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

    public static MeshStatusResponse fromByteBuffer(ByteBuffer buffer) {

        MeshStatusResponse result = null;

        try {
            int numberOfLines = buffer.getShort() & 0xffff;
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < numberOfLines; i++) {
                short lineByteLength = buffer.getShort();
                byte[] lineBytes = new byte[lineByteLength];
                buffer.get(lineBytes);
                lines.add(new String(lineBytes, StandardCharsets.UTF_8));
            }

            result = new MeshStatusResponse(lines);

        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    @Override
    public String toString() {

        return "[MeshStatusResponse(lines=" + lines.size() + ")]";
    }
}
