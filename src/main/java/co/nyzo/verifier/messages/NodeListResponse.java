package co.nyzo.verifier.messages;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.MessageObject;
import co.nyzo.verifier.Node;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class NodeListResponse implements MessageObject {

    private List<Node> nodes;

    public NodeListResponse(List<Node> nodes) {
        this.nodes = nodes;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    @Override
    public int getByteSize() {
        // list_size + #_nodes * (identifier + IP address + port)
        return FieldByteSize.nodeListLength + nodes.size() * (FieldByteSize.identifier + FieldByteSize.ipAddress +
                FieldByteSize.port + FieldByteSize.booleanField);
    }

    @Override
    public byte[] getBytes() {

        int size = getByteSize();
        System.out.println("byte size of node list response: " + size);
        System.out.println("node list size: " + nodes.size());
        byte[] result = new byte[size];
        ByteBuffer buffer = ByteBuffer.wrap(result);
        buffer.putInt(nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            buffer.put(node.getIdentifier());
            buffer.put(node.getIpAddress());
            buffer.putInt(node.getPort());
            buffer.put(node.isFullNode() ? (byte) 1 : (byte) 0);
        }

        return result;
    }

    public static NodeListResponse fromByteBuffer(ByteBuffer buffer) {

        NodeListResponse result = null;

        try {
            List<Node> nodes = new ArrayList<>();

            int numberOfNodes = buffer.getInt();
            for (int i = 0; i < numberOfNodes; i++) {
                byte[] identifier = new byte[FieldByteSize.identifier];
                buffer.get(identifier);
                byte[] ipAddress = new byte[FieldByteSize.ipAddress];
                buffer.get(ipAddress);
                int port = buffer.getInt();
                boolean fullNode = buffer.get() == 1;

                nodes.add(new Node(identifier, ipAddress, port, fullNode));
            }

            result = new NodeListResponse(nodes);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("[NodeListResponse(" + nodes.size() + "): {");
        String separator = "";
        for (int i = 0; i < nodes.size() && i < 5; i++) {
            result.append(separator + nodes.get(i).toString());
            separator = ",";
        }
        if (nodes.size() > 5) {
            result.append("...");
        }
        result.append("}]");

        return result.toString();
    }
}
