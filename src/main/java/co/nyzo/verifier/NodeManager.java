package co.nyzo.verifier;

import co.nyzo.verifier.messages.*;
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

    private static final int minimumMissingNodeRequestInterval = 30;
    private static int missingNodeRequestWait = minimumMissingNodeRequestInterval;

    public static void updateNode(Message message) {

        if (message.getType() == MessageType.BootstrapRequest1 || message.getType() == MessageType.NodeJoin3 ||
                message.getType() == MessageType.NodeJoinResponse4 || message.getType() == MessageType.NewBlock9) {

            int port = ((PortMessage) message.getContent()).getPort();
            boolean isNewNode = updateNode(message.getSourceNodeIdentifier(), message.getSourceIpAddress(), port);
            if (isNewNode) {
                Message.fetch(IpUtil.addressAsString(message.getSourceIpAddress()), port,
                        new Message(MessageType.NodeJoin3, new NodeJoinMessage()), null);
            }

        } else {

            NotificationUtil.send("unrecognized message type in updateNode(): " + message.getType());
        }
    }

    public static void addTemporaryLocalVerifierEntry() {

        updateNode(Verifier.getIdentifier(), new byte[4], 0);
    }

    private static synchronized boolean updateNode(byte[] identifier, byte[] ipAddress, int port) {

        boolean isNewNode = false;
        if (identifier != null && identifier.length == FieldByteSize.identifier && ipAddress != null &&
                ipAddress.length == FieldByteSize.ipAddress && !IpUtil.isPrivate(ipAddress)) {

            // Try to get the node from the map.
            ByteBuffer ipAddressBuffer = ByteBuffer.wrap(ipAddress);
            Node existingNode = ipAddressToNodeMap.get(ipAddressBuffer);

            if (existingNode == null) {
                // This is the case when no other node is at the IP. We create a new node and add it to the map.
                Node node = new Node(identifier, ipAddress, port);
                ipAddressToNodeMap.put(ipAddressBuffer, node);
                isNewNode = true;

                // If the node that was just added is the local verifier and not the temporary entry, remove the
                // temporary entry.
                if (!ByteUtil.isAllZeros(ipAddress) && ByteUtil.arraysAreEqual(identifier, Verifier.getIdentifier())) {
                    ipAddressToNodeMap.remove(ByteBuffer.wrap(new byte[4]));
                }

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
                    isNewNode = true;
                }
            }
        }

        return isNewNode;
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

            // Only mark a node inactive if the consecutive failure count has been exceeded and the node is not in the
            // current cycle.
            Node node = ipAddressToNodeMap.get(addressBuffer);
            if (count < consecutiveFailuresBeforeRemoval || node == null ||
                    BlockManager.verifierInCurrentCycle(ByteBuffer.wrap(node.getIdentifier()))) {
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
        if (node != null) {
            node.setInactiveTimestamp(System.currentTimeMillis());
        }
    }

    public static boolean isActive(byte[] verifierIdentifier) {

        return ByteUtil.arraysAreEqual(verifierIdentifier, Verifier.getIdentifier()) ||
                activeVerifiers.contains(ByteBuffer.wrap(verifierIdentifier));
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
                NotificationUtil.send("removed node " + NicknameManager.get(node.getIdentifier()) + " from mesh on " +
                        Verifier.getNickname());
            }
        }

        NodeManager.activeVerifiers = activeVerifiers;
    }

    public static void sendNodeJoinMessage(byte[] ipAddress, int port) {

        Message nodeJoinMessage = new Message(MessageType.NodeJoin3, new NodeJoinMessage());
        Message.fetch(IpUtil.addressAsString(ipAddress), port, nodeJoinMessage, new MessageCallback() {
            @Override
            public void responseReceived(Message message) {
                if (message != null) {

                    updateNode(message);

                    NodeJoinResponse response = (NodeJoinResponse) message.getContent();
                    if (response != null) {

                        NicknameManager.put(message.getSourceNodeIdentifier(), response.getNickname());
                        for (BlockVote vote : response.getBlockVotes()) {
                            BlockVoteManager.registerVote(message.getSourceNodeIdentifier(), vote);
                        }

                        if (!ByteUtil.isAllZeros(response.getNewVerifierVote().getIdentifier())) {
                            NewVerifierVoteManager.registerVote(message.getSourceNodeIdentifier(),
                                    response.getNewVerifierVote(), false);
                        }
                    }
                }
            }
        });

    }

    public static synchronized void requestMissingNodes() {

        // Once per cycle, but no more frequently than every 30 iterations, request the mesh from an arbitrary node and
        // send a node-join request to every node in that node's response. This helps to ensure a tightly integrated
        // mesh, with all nodes being aware of all other nodes.
        if (missingNodeRequestWait-- <= 0) {

            missingNodeRequestWait = Math.max(BlockManager.currentCycleLength(), minimumMissingNodeRequestInterval);

            Message meshRequest = new Message(MessageType.MeshRequest15, null);
            Message.fetchFromRandomNode(meshRequest, new MessageCallback() {
                @Override
                public void responseReceived(Message message) {

                    // Send node-join requests to all nodes in the response.
                    MeshResponse response = (MeshResponse) message.getContent();
                    for (Node node : response.getMesh()) {
                        NodeManager.sendNodeJoinMessage(node.getIpAddress(), node.getPort());
                    }
                }
            });
        }
    }
}
