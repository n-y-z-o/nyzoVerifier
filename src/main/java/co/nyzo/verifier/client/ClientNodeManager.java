package co.nyzo.verifier.client;

import co.nyzo.verifier.Node;
import co.nyzo.verifier.messages.MeshResponse;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientNodeManager {

    // This is a much simpler class than the NodeManager class used by the verifier. Unlike the verifier, the client
    // will never have any voting rights in the cycle, and it will never send node joins to any other nodes.

    // As the client develops, protections will be put in place to avoid malicious responses that fill the client node
    // list with invalid entries. For simplicity in the initial release, nodes are accepted without vetting.
    private static Map<ByteBuffer, Node> nodes = new ConcurrentHashMap<>();

    public static void processMeshResponse(MeshResponse meshResponse) {

        if (meshResponse != null && meshResponse.getMesh() != null) {
            for (Node node : meshResponse.getMesh()) {
                nodes.put(ByteBuffer.wrap(node.getIpAddress()), node);
            }
        }
    }

    public static Node randomNode() {
        return randomElement(nodes.values());
    }

    private static Node randomElement(Collection<Node> collection) {

        Node node = null;
        if (!collection.isEmpty()) {
            List<Node> list = new ArrayList<>(collection);
            node = list.get(new Random().nextInt(list.size()));
        }

        return node;
    }

    public static Collection<Node> getMesh() {
        return nodes.values();
    }
}
