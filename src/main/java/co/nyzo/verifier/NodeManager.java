package co.nyzo.verifier;

import co.nyzo.verifier.messages.*;
import co.nyzo.verifier.util.FileUtil;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.NotificationUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class NodeManager {

    private static Set<ByteBuffer> activeIdentifiers = new HashSet<>();
    private static Set<ByteBuffer> activeCycleIdentifiers = new HashSet<>();
    private static String missingInCycleVerifiers = "";
    private static final Map<ByteBuffer, Node> ipAddressToNodeMap = new HashMap<>();

    private static final int consecutiveFailuresBeforeRemoval = 6;
    private static final Map<ByteBuffer, Integer> ipAddressToFailureCountMap = new HashMap<>();

    private static final int minimumMeshRequestInterval = 30;
    private static int meshRequestWait = minimumMeshRequestInterval;

    private static final Map<ByteBuffer, Integer> nodeJoinRequestQueue = new HashMap<>();
    private static final AtomicInteger nodeJoinRequestsSent = new AtomicInteger(0);
    private static final Map<ByteBuffer, Long> persistedQueueTimestamps = new HashMap<>();

    public static final File queueTimestampsFile = new File(Verifier.dataRootDirectory, "queue_timestamps");

    static {
        loadPersistedQueueTimestamps();
    }

    public static void updateNode(Message message) {

        // In previous versions, more types of requests were registered to increase mesh density. However, to make the
        // system more flexible, we have changed this to only update a node when explicitly requested to do so through
        // a node join.
        if (message.getType() == MessageType.NodeJoin3 || message.getType() == MessageType.NodeJoinResponse4) {

            int port = ((PortMessage) message.getContent()).getPort();
            boolean isNewNode = updateNode(message.getSourceNodeIdentifier(), message.getSourceIpAddress(), port);
            if (isNewNode) {
                Message.fetch(IpUtil.addressAsString(message.getSourceIpAddress()), port,
                        new Message(MessageType.NodeJoin3, new NodeJoinMessage()), null);
            }

        } else if (message.getType() == MessageType.MissingBlockVoteRequest23 ||
                message.getType() == MessageType.MissingBlockRequest25) {

            // This is not a full update. Instead, to offset our marking of in-cycle nodes as inactive, we allow a
            // missing block vote request or a missing block request to reactivate the node. These requests are
            // typically made when a node comes back online after a temporary network issue.
            Node node = ipAddressToNodeMap.get(ByteBuffer.wrap(message.getSourceIpAddress()));
            if (node != null) {
                node.setInactiveTimestamp(-1);
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
                long persistedTimestamp = persistedQueueTimestamps.getOrDefault(ByteBuffer.wrap(identifier), 0L);
                if (persistedTimestamp > 0L && persistedTimestamp < node.getQueueTimestamp()) {
                    node.setQueueTimestamp(persistedTimestamp);
                }
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

    public static synchronized void demoteIdentifier(byte[] identifier) {

        System.out.println("demoting verifier " + NicknameManager.get(identifier));

        // Reset the queue timestamp of matching nodes.
        for (Node node : ipAddressToNodeMap.values()) {
            if (ByteUtil.arraysAreEqual(node.getIdentifier(), identifier)) {
                node.setQueueTimestamp(System.currentTimeMillis());
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

    public static int getNumberOfActiveIdentifiers() {
        return activeIdentifiers.size();
    }

    public static int getNumberOfActiveCycleIdentifiers() {
        return activeCycleIdentifiers.size();
    }

    public static String getMissingInCycleVerifiers() {
        return missingInCycleVerifiers;
    }

    public static int getNodeJoinRequestsSent() {
        return nodeJoinRequestsSent.get();
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

            // Only mark a node inactive if the consecutive failure count has been exceeded.
            Node node = ipAddressToNodeMap.get(addressBuffer);
            if (node == null || count < consecutiveFailuresBeforeRemoval) {
                ipAddressToFailureCountMap.put(addressBuffer, count);
            } else {
                ipAddressToFailureCountMap.remove(addressBuffer);
                markNodeAsInactive(addressBuffer);
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

    private static void markNodeAsInactive(ByteBuffer addressBuffer) {

        Node node = ipAddressToNodeMap.get(addressBuffer);
        if (node != null && node.isActive()) {
            node.setInactiveTimestamp(System.currentTimeMillis());
        }
    }

    public static boolean isActive(byte[] verifierIdentifier) {

        return ByteUtil.arraysAreEqual(verifierIdentifier, Verifier.getIdentifier()) ||
                activeIdentifiers.contains(ByteBuffer.wrap(verifierIdentifier));
    }

    public static synchronized void updateActiveVerifiersAndRemoveOldNodes() {

        Set<ByteBuffer> currentCycle = BlockManager.verifiersInCurrentCycleSet();

        Set<ByteBuffer> activeIdentifiers = new HashSet<>();
        Set<ByteBuffer> activeCycleIdentifiers = new HashSet<>();
        long thresholdTimestamp = System.currentTimeMillis() - Block.blockDuration *
                BlockManager.currentCycleLength() * 2;
        for (ByteBuffer ipAddress : new HashSet<>(ipAddressToNodeMap.keySet())) {
            Node node = ipAddressToNodeMap.get(ipAddress);
            if (node.isActive()) {
                ByteBuffer identifierBuffer = ByteBuffer.wrap(node.getIdentifier());
                activeIdentifiers.add(identifierBuffer);
                if (currentCycle.contains(identifierBuffer)) {
                    activeCycleIdentifiers.add(identifierBuffer);
                }
            } else if (node.getInactiveTimestamp() < thresholdTimestamp) {
                ipAddressToNodeMap.remove(ipAddress);
                NotificationUtil.send("removed node " + NicknameManager.get(node.getIdentifier()) + " from mesh on " +
                        Verifier.getNickname());
            }
        }

        StringBuilder missingInCycleVerifiers;
        if (activeCycleIdentifiers.size() == currentCycle.size()) {
            missingInCycleVerifiers = new StringBuilder("*** no verifiers missing ***");
        } else {
            missingInCycleVerifiers = new StringBuilder();
            String separator = "";
            for (ByteBuffer identifier : currentCycle) {
                if (!activeCycleIdentifiers.contains(identifier)) {
                    missingInCycleVerifiers.append(separator).append(NicknameManager.get(identifier.array()));
                    separator = ",";
                }
            }
        }

        NodeManager.activeIdentifiers = activeIdentifiers;
        NodeManager.activeCycleIdentifiers = activeCycleIdentifiers;
        NodeManager.missingInCycleVerifiers = missingInCycleVerifiers.toString();
    }

    public static void enqueueNodeJoinMessage(byte[] ipAddress, int port) {

        nodeJoinRequestQueue.put(ByteBuffer.wrap(ipAddress), port);
    }

    public static void sendNodeJoinRequests(int count) {

        // This method is called from multiple places, and threading issues could result in an odd state for the
        // queue. Rather than adding synchronization or logic to deal with this, the try/catch will ensure that
        // any exceptions do not leave this method.
        try {
            // A positive value indicates the specified number of requests should be sent from the queue, emptying the
            // queue if the queue size is less than or equal to the specified number. A negative number indicates that
            // the queue should be emptied, regardless of its size.
            if (count < 0) {
                count = nodeJoinRequestQueue.size();
            }

            for (int i = 0; i < count && !nodeJoinRequestQueue.isEmpty(); i++) {

                ByteBuffer ipAddressBuffer = nodeJoinRequestQueue.keySet().iterator().next();
                Integer port = nodeJoinRequestQueue.remove(ipAddressBuffer);

                if (port != null && port > 0) {
                    nodeJoinRequestsSent.incrementAndGet();
                    Message nodeJoinMessage = new Message(MessageType.NodeJoin3, new NodeJoinMessage());
                    Message.fetch(IpUtil.addressAsString(ipAddressBuffer.array()), port, nodeJoinMessage,
                            new MessageCallback() {
                                @Override
                                public void responseReceived(Message message) {
                                    if (message != null) {

                                        updateNode(message);

                                        NodeJoinResponse response = (NodeJoinResponse) message.getContent();
                                        if (response != null) {

                                            NicknameManager.put(message.getSourceNodeIdentifier(),
                                                    response.getNickname());

                                            if (!ByteUtil.isAllZeros(response.getNewVerifierVote().getIdentifier())) {
                                                NewVerifierVoteManager.registerVote(message.getSourceNodeIdentifier(),
                                                        response.getNewVerifierVote(), false);
                                            }
                                        }
                                    }
                                }
                            });
                }
            }
        } catch (Exception ignored) { }
    }

    public static synchronized void requestMesh() {

        // Once per cycle, but no more frequently than every 30 iterations, and only when the queue of node-join
        // requests is empty, request the mesh from an arbitrary node and enqueue a node-join request to every node
        // in that node's response. This helps to ensure a tightly integrated mesh, with all nodes being aware of all
        // other nodes. This also periodically checks to ensure that waiting nodes are still online, eventually
        // removing them if they disappear for too long.
        if (meshRequestWait-- <= 0 && nodeJoinRequestQueue.isEmpty()) {

            meshRequestWait = Math.max(BlockManager.currentCycleLength(), minimumMeshRequestInterval);

            Message meshRequest = new Message(MessageType.MeshRequest15, null);
            Message.fetchFromRandomNode(meshRequest, new MessageCallback() {
                @Override
                public void responseReceived(Message message) {

                    if (message == null) {
                        // For a null response, set the mesh request wait to zero so that another attempt will be made
                        // on the next iteration.
                        meshRequestWait = 0;
                    } else {
                        // Queue node-join requests to all nodes in the response.
                        MeshResponse response = (MeshResponse) message.getContent();
                        for (Node node : response.getMesh()) {
                            NodeManager.enqueueNodeJoinMessage(node.getIpAddress(), node.getPort());
                        }

                        System.out.println("reloaded node-join request queue, size is now " +
                                nodeJoinRequestQueue.size());
                    }
                }
            });
        }
    }

    public static synchronized void demoteInCycleNodes() {

        for (Node node : ipAddressToNodeMap.values()) {

            if (BlockManager.verifierInCurrentCycle(ByteBuffer.wrap(node.getIdentifier()))) {
                node.setQueueTimestamp(System.currentTimeMillis());
            }
        }
    }

    public static void persistQueueTimestamps() {

        List<Node> mesh = getMesh();
        byte[] array = new byte[mesh.size() * (FieldByteSize.identifier + FieldByteSize.timestamp)];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        for (Node node : mesh) {
            buffer.put(node.getIdentifier());
            buffer.putLong(node.getQueueTimestamp());
        }

        Path path = Paths.get(queueTimestampsFile.getAbsolutePath());
        FileUtil.writeFile(path, array);
    }

    private static void loadPersistedQueueTimestamps() {

        // This method is called in the class's static block. We load the queue timestamps into the map, and they are
        // used as nodes are created.
        Path path = Paths.get(queueTimestampsFile.getAbsolutePath());
        try {
            byte[] array = Files.readAllBytes(path);
            ByteBuffer buffer = ByteBuffer.wrap(array);
            int numberOfEntries = array.length / (FieldByteSize.identifier + FieldByteSize.timestamp);
            for (int i = 0; i < numberOfEntries; i++) {
                byte[] identifier = Message.getByteArray(buffer, FieldByteSize.identifier);
                long timestamp = buffer.getLong();
                persistedQueueTimestamps.put(ByteBuffer.wrap(identifier), timestamp);
            }
        } catch (Exception ignored) { }
    }
}
