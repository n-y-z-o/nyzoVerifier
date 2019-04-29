package co.nyzo.verifier.messages;

import co.nyzo.verifier.BlockManager;
import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.MessageObject;
import co.nyzo.verifier.Node;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MeshResponse implements MessageObject {

    private static final int maximumNumberOfNodes = 10000;

    private List<Node> mesh;

    public MeshResponse(List<Node> mesh) {

        // If the list is too big, shuffle the list to ensure that different nodes are removed each time.
        if (mesh.size() > maximumNumberOfNodes) {
            Collections.shuffle(mesh);
        }

        // If the list is too big, remove out-of-cycle nodes first.
        for (int i = mesh.size() - 1; i >= 0 && mesh.size() > maximumNumberOfNodes; i--) {
            if (!BlockManager.verifierInCurrentCycle(ByteBuffer.wrap(mesh.get(i).getIdentifier()))) {
                mesh.remove(i);
            }
        }

        // If the list is still too big, create a sublist.
        if (mesh.size() > maximumNumberOfNodes) {
            mesh = mesh.subList(0, maximumNumberOfNodes);
        }

        this.mesh = mesh;
    }

    public List<Node> getMesh() {
        return mesh;
    }

    @Override
    public int getByteSize() {

        return FieldByteSize.nodeListLength + mesh.size() * (Node.getByteSizeStatic());
    }

    @Override
    public byte[] getBytes() {

        byte[] result = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(result);
        buffer.putInt(mesh.size());
        for (Node node : mesh) {
            buffer.put(node.getBytes());
        }

        return result;
    }

    public static MeshResponse fromByteBuffer(ByteBuffer buffer) {

        MeshResponse result = null;

        try {
            List<Node> mesh = new ArrayList<>();

            int numberOfNodes = Math.min(buffer.getInt(), maximumNumberOfNodes);
            for (int i = 0; i < numberOfNodes; i++) {
                mesh.add(Node.fromByteBuffer(buffer));
            }

            result = new MeshResponse(mesh);
        } catch (Exception ignored) { }

        return result;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("[MeshResponse(" + mesh.size() + "): {");
        String separator = "";
        for (int i = 0; i < mesh.size() && i < 5; i++) {
            result.append(separator).append(mesh.get(i).toString());
            separator = ",";
        }
        if (mesh.size() > 5) {
            result.append("...");
        }
        result.append("}]");

        return result.toString();
    }
}
