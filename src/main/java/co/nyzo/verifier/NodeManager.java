package co.nyzo.verifier;

import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.NotificationUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class NodeManager {

    private static Set<ByteBuffer> activeVerifiers = new HashSet<>();
    private static final Map<ByteBuffer, Node> ipAddressToNodeMap = new HashMap<>();

    private static final int consecutiveFailuresBeforeRemoval = 6;
    private static final Map<ByteBuffer, Integer> ipAddressToFailureCountMap = new HashMap<>();

    public static void updateNode(byte[] identifier, byte[] ipAddress, int port) {

        updateNode(identifier, ipAddress, port, 0);
    }

    public static synchronized void updateNode(byte[] identifier, byte[] ipAddress, int port, long queueTimestamp) {

        System.out.println("adding node " + PrintUtil.compactPrintByteArray(identifier) + ", " +
                IpUtil.addressAsString(ipAddress));

        if (identifier != null && identifier.length == FieldByteSize.identifier && ipAddress != null &&
                ipAddress.length == FieldByteSize.ipAddress && !IpUtil.isPrivate(ipAddress)) {

            // Try to get the node from the map.
            ByteBuffer ipAddressBuffer = ByteBuffer.wrap(ipAddress);
            Node existingNode = ipAddressToNodeMap.get(ipAddressBuffer);

            if (existingNode == null) {
                // This is the case when no other node is at the IP. We create a new node and add it to the map.
                Node node = new Node(identifier, ipAddress, port);
                if (queueTimestamp > 0) {
                    node.setQueueTimestamp(queueTimestamp);
                }
                ipAddressToNodeMap.put(ipAddressBuffer, node);

            } else {
                // This is the case when there is already a node at the IP. We always update the port and mark the node
                // as active. Then, if the verifier has changed and a verifier change is allowed, we update the
                // verifier.

                existingNode.setPort(port);
                existingNode.setInactiveTimestamp(-1L);

                if (!ByteUtil.arraysAreEqual(existingNode.getIdentifier(), identifier) &&
                        verifierChangeAllowed(existingNode)) {
                    existingNode.setIdentifier(identifier);
                    existingNode.setQueueTimestamp(System.currentTimeMillis());
                }
            }
        }
    }

    private static boolean verifierChangeAllowed(Node node) {

        long blocksSinceChange = (System.currentTimeMillis() - node.getQueueTimestamp()) / Block.blockDuration;
        return blocksSinceChange > BlockManager.currentCycleLength() + 2;
    }

    public static synchronized List<Node> getMesh() {
        return new ArrayList<>(ipAddressToNodeMap.values());
    }

    public static int getMeshSize() {
        return ipAddressToNodeMap.size();
    }

    public static int getActiveMeshSize() {
        return activeVerifiers.size();
    }

    public static boolean connectedToMesh() {

        // When we request the node list from another node, it will add this node to the list. So, the minimum number
        // of nodes in a proper mesh is two.
        return ipAddressToNodeMap.size() > 1;
    }

    public static byte[] identifierForIpAddress(String addressString) {

        byte[] identifier = null;
        byte[] address = IpUtil.addressFromString(addressString);
        if (address != null) {
            ByteBuffer addressBuffer = ByteBuffer.wrap(address);
            Node node = ipAddressToNodeMap.get(addressBuffer);
            if (node != null) {
                identifier = node.getIdentifier();
            }
        }

        return identifier;
    }

    public static void markFailedConnection(String addressString) {

        byte[] address = IpUtil.addressFromString(addressString);
        if (address != null) {
            ByteBuffer addressBuffer = ByteBuffer.wrap(address);
            Integer count = ipAddressToFailureCountMap.get(addressBuffer);
            if (count == null) {
                count = 1;
            } else {
                count++;
            }

            if (count < consecutiveFailuresBeforeRemoval) {
                ipAddressToFailureCountMap.put(addressBuffer, count);
            } else {
                ipAddressToFailureCountMap.remove(addressBuffer);
                removeNodeFromMesh(addressBuffer);
            }
        }
    }

    public static void markSuccessfulConnection(String addressString) {

        byte[] address = IpUtil.addressFromString(addressString);
        if (address != null) {
            ByteBuffer addressBuffer = ByteBuffer.wrap(address);
            ipAddressToFailureCountMap.remove(addressBuffer);
            Node node = ipAddressToNodeMap.get(addressBuffer);
            if (node != null) {
                node.setInactiveTimestamp(-1L);
            }
        }
    }

    private static synchronized void removeNodeFromMesh(ByteBuffer addressBuffer) {

        Node node = ipAddressToNodeMap.get(addressBuffer);
        node.setInactiveTimestamp(System.currentTimeMillis());
    }

    public static boolean isActive(byte[] verifierIdentifier) {

        return activeVerifiers.contains(ByteBuffer.wrap(verifierIdentifier));
    }

    public static synchronized void updateActiveVerifiersAndRemoveOldNodes() {

        Set<ByteBuffer> activeVerifiers = new HashSet<>();
        long thresholdTimestamp = System.currentTimeMillis() - Block.blockDuration *
                BlockManager.currentCycleLength() * 2;
        for (ByteBuffer ipAddress : new HashSet<>(ipAddressToNodeMap.keySet())) {
            Node node = ipAddressToNodeMap.get(ipAddress);
            if (node.isActive()) {
                activeVerifiers.add(ByteBuffer.wrap(node.getIdentifier()));
            } else if (node.getInactiveTimestamp() < thresholdTimestamp) {
                ipAddressToNodeMap.remove(ipAddress);
                NotificationUtil.send("removed node " + NicknameManager.get(node.getIdentifier()) + " from mesh");
            }
        }

        NodeManager.activeVerifiers = activeVerifiers;
    }
}
