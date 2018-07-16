package co.nyzo.verifier.messages.debug;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.MultilineTextResponse;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MeshStatusResponse implements MessageObject, MultilineTextResponse {

    private List<String> lines;

    public MeshStatusResponse(Message request) {

        // This is a debug request, so it must be signed by the local verifier.
        if (ByteUtil.arraysAreEqual(request.getSourceNodeIdentifier(), Verifier.getIdentifier())) {

            List<Node> nodes = NodeManager.getMesh();
            Collections.sort(nodes, new Comparator<Node>() {
                @Override
                public int compare(Node node1, Node node2) {
                    String nickname1 = NicknameManager.get(node1.getIdentifier());
                    String nickname2 = NicknameManager.get(node2.getIdentifier());
                    return nickname1.compareTo(nickname2);
                }
            });

            List<String> lines = new ArrayList<>();
            for (int i = 0; i < nodes.size() && i < 100; i++) {
                lines.add(NicknameManager.get(nodes.get(i).getIdentifier()));
            }

            this.lines = lines;
        } else {
            this.lines = new ArrayList<>();
        }
    }

    public MeshStatusResponse(List<String> lines) {

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

    public static MeshStatusResponse fromByteBuffer(ByteBuffer buffer) {

        MeshStatusResponse result = null;

        try {
            int numberOfLines = buffer.get() & 0xff;
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
        StringBuilder result = new StringBuilder("[MeshStatusResponse(lines=" + lines.size() + ")]");

        return result.toString();
    }
}
