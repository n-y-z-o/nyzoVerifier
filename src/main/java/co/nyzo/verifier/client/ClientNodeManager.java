package co.nyzo.verifier.client;

import co.nyzo.verifier.Node;
import co.nyzo.verifier.messages.MeshResponse;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientNodeManager {

    // This is a much simpler class than the NodeManager class used by the verifier. Unlike the verifier, the client
    // will never have any voting rights in the cycle, and it will never send node joins to any other nodes.

    // Separation of confirmed nodes and pending nodes helps to reduce the problems caused by a node providing a list
    // of invalid nodes.
    private static Map<ByteBuffer, Node> confirmedNodes = new ConcurrentHashMap<>();
    private static Map<ByteBuffer, Node> pendingNodes = new ConcurrentHashMap<>();

    public static void processMeshResponse(MeshResponse meshResponse) {

        if (meshResponse.getMesh() != null) {

            // To prevent other nodes from spamming the node list with bad nodes, a limit is placed on the number of
            // nodes accepted from any one node.
            int numberOfNodesAdded = 0;

            for (Node node : meshResponse.getMesh()) {

                // This check of containment followed by an addition to pending nodes is not strictly thread-safe,
                // but the failure of atomicity will not cause significant problems.
                ByteBuffer ipAddress = ByteBuffer.wrap(node.getIpAddress());
                if (!confirmedNodes.containsKey(ipAddress) && !pendingNodes.containsKey(ipAddress) &&
                        numberOfNodesAdded < 100) {
                    numberOfNodesAdded++;
                    pendingNodes.put(ipAddress, node);
                }
            }
        }
    }

    public static Node randomConfirmedNode() {

        Node node;
        if (confirmedNodes.isEmpty()) {
            node = randomElement(pendingNodes.values());
        } else {
            node = randomElement(confirmedNodes.values());
        }

        return node;
    }

    private static Node randomElement(Collection<Node> collection) {

        Node node = null;
        if (!collection.isEmpty()) {
            List<Node> list = new ArrayList<>(collection);
            node = list.get(new Random().nextInt(list.size()));
        }

        return node;
    }
}
