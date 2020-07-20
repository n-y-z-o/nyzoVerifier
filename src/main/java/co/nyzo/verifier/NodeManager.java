package co.nyzo.verifier;

import co.nyzo.verifier.messages.*;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.LogUtil;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class NodeManager {

    private static Set<ByteBuffer> activeCycleIdentifiers = ConcurrentHashMap.newKeySet();
    private static Set<ByteBuffer> activeCycleIpAddresses = ConcurrentHashMap.newKeySet();
    private static String missingInCycleVerifiers = "";

    private static final int maximumNodesPerInCycleVerifier = 6;
    private static final int maximumNewNodeMapSize = 1000;
    private static Map<ByteBuffer, Integer> newNodeIpToPortMap = new ConcurrentHashMap<>();

    private static final Map<ByteBuffer, Node> ipAddressToNodeMap = new ConcurrentHashMap<>();

    private static final int minimumMeshRequestInterval = 30;
    private static AtomicInteger meshRequestWait = new AtomicInteger(minimumMeshRequestInterval);
    private static AtomicInteger meshRequestSuccessCount = new AtomicInteger(0);

    private static final Map<ByteBuffer, Integer> nodeJoinRequestQueue = new ConcurrentHashMap<>();
    private static final AtomicInteger nodeJoinRequestsSent = new AtomicInteger(0);

    private static final String haveNodeHistoryKey = "have_node_history";
    private static boolean haveNodeHistory = PersistentData.getBoolean(haveNodeHistoryKey, false);
    public static final File nodeFile = new File(Verifier.dataRootDirectory, "nodes");

    static {
        loadPersistedNodes();
    }

    public static void updateNode(Message message) {

        // In previous versions, more types of requests were registered to increase mesh density. However, to make the
        // system more flexible, we have changed this to only update a node when explicitly requested to do so through
        // a node join.
        if (message.getType() == MessageType.NodeJoinV2_43 || message.getType() == MessageType.NodeJoinResponseV2_44) {

            PortMessageV2 portMessage = (PortMessageV2) message.getContent();
            int portTcp = portMessage.getPortTcp();
            int portUdp = portMessage.getPortUdp();

            // Determine whether this is a node-join response. This is one of the pieces of information used to
            // determine whether a node is added to the map immediately or if it is deferred to the node-join queue.
            boolean isNodeJoinResponse = message.getType() == MessageType.NodeJoinResponseV2_44;

            // Update the node.
            updateNode(message.getSourceNodeIdentifier(), message.getSourceIpAddress(), portTcp, portUdp,
                    isNodeJoinResponse);

        } else if (message.getType() == MessageType.MissingBlockVoteRequest23 ||
                message.getType() == MessageType.MissingBlockRequest25) {

            // This is not a full update. Instead, to offset our marking of in-cycle nodes as inactive, we allow a
            // missing block vote request or a missing block request to reactivate the node. These requests are
            // typically made when a node comes back online after a temporary network issue.
            Node node = ipAddressToNodeMap.get(ByteBuffer.wrap(message.getSourceIpAddress()));
            if (node != null) {
                ByteBuffer identifierBuffer = ByteBuffer.wrap(node.getIdentifier());
                if (BlockManager.verifierInCurrentCycle(identifierBuffer)) {
                    node.markSuccessfulConnection();
                } else {
                    LogUtil.println("Missing block request from out of cycle in updateNode(): " + NicknameManager.get(node.getIdentifier()));
                }
            }
        } else {
            LogUtil.println("unrecognized message type in updateNode(): " + message.getType());
        }
    }

    public static void addTemporaryLocalVerifierEntry() {
        updateNode(Verifier.getIdentifier(), new byte[4], 0, 0, true);
    }

    private static void updateNode(byte[] identifier, byte[] ipAddress, int portTcp, int portUdp,
                                   boolean isNodeJoinResponse) {

        if (identifier != null && identifier.length == FieldByteSize.identifier && ipAddress != null &&
                ipAddress.length == FieldByteSize.ipAddress && !IpUtil.isPrivate(ipAddress)) {

            // Try to get the node from the map.
            ByteBuffer ipAddressBuffer = ByteBuffer.wrap(ipAddress);
            Node existingNode = ipAddressToNodeMap.get(ipAddressBuffer);

            if (existingNode != null && ByteUtil.arraysAreEqual(existingNode.getIdentifier(), identifier)) {
                // This is the case when there is already a node at the IP with the same identifier. Update the ports
                // and mark a successful connection.
                existingNode.setPortTcp(portTcp);
                if (portUdp > 0) {
                    existingNode.setPortUdp(portUdp);
                }
                if (isNodeJoinResponse) {
                    existingNode.markSuccessfulConnection();
                }
            } else {
                // If the existing node is not null, remove it.
                if (existingNode != null) {
                    ipAddressToNodeMap.remove(ipAddressBuffer);
                }

                // Now, determine what to do with the new node.
                ByteBuffer identifierBuffer = ByteBuffer.wrap(identifier);
                if (BlockManager.verifierInCurrentCycle(identifierBuffer) || isNodeJoinResponse) {
                    // All in-cycle nodes, in addition to out-of-cycle nodes due to node-join responses, are added now,
                    // subject to a limit per verifier. Set the timestamp of the node so that it is immediately eligible
                    // for the lottery if sufficient history is not present.
                    int instanceCount = 0;
                    for (Node mapNode : ipAddressToNodeMap.values()) {
                        if (ByteUtil.arraysAreEqual(mapNode.getIdentifier(), identifier)) {
                            instanceCount++;
                        }
                    }
                    if (instanceCount < maximumNodesPerInCycleVerifier) {
                        Node node = new Node(identifier, ipAddress, portTcp, portUdp);
                        if (!haveNodeHistory) {
                            node.setQueueTimestamp(System.currentTimeMillis() -
                                    NewVerifierQueueManager.lotteryWaitTime);
                        }
                        ipAddressToNodeMap.put(ipAddressBuffer, node);
                        if (!BlockManager.verifierInCurrentCycle(identifierBuffer)) {
                            LogUtil.println("added new out-of-cycle node to NodeManager: " +
                                    NicknameManager.get(identifier));
                        }
                    }
                } else {
                    // Out-of-cycle nodes due to node joins are added to a map for later querying.
                    newNodeIpToPortMap.put(ipAddressBuffer, portTcp);
                    LogUtil.println("added new out-of-cycle node to queue: " + NicknameManager.get(identifier));
                    if (newNodeIpToPortMap.size() > maximumNewNodeMapSize) {
                        newNodeIpToPortMap.remove(newNodeIpToPortMap.keySet().iterator().next());
                        LogUtil.println("removed node from new out-of-cycle queue due to size");
                    }
                }

                // If the node that was just processed is the local verifier and not the temporary entry, remove the
                // temporary entry.
                if (!ByteUtil.isAllZeros(ipAddress) &&
                        ByteUtil.arraysAreEqual(identifier, Verifier.getIdentifier())) {
                    ipAddressToNodeMap.remove(ByteBuffer.wrap(new byte[4]));
                }
            }
        }
    }

    public static void demoteIdentifier(byte[] identifier) {

        System.out.println("demoting verifier " + NicknameManager.get(identifier));

        // Reset the queue timestamp of matching nodes.
        for (Node node : ipAddressToNodeMap.values()) {
            if (ByteUtil.arraysAreEqual(node.getIdentifier(), identifier)) {
                node.setQueueTimestamp(System.currentTimeMillis());
            }
        }
    }

    public static List<Node> getCycle() {

        List<Node> cycleNodes = new ArrayList<>();
        for (Node node : ipAddressToNodeMap.values()) {
            if (BlockManager.verifierInCurrentCycle(ByteBuffer.wrap(node.getIdentifier()))) {
                cycleNodes.add(node);
            }
        }

        return cycleNodes;
    }

    public static List<Node> getMesh() {
        return new ArrayList<>(ipAddressToNodeMap.values());
    }

    public static int getMeshSizeForGenesisCycleVoting() {
        Set<ByteBuffer> identifiers = new HashSet<>();
        for (Node node : ipAddressToNodeMap.values()) {
            identifiers.add(ByteBuffer.wrap(node.getIdentifier()));
        }
        return identifiers.size();
    }

    public static int getNumberOfNodesInMap() {
        return ipAddressToNodeMap.size();
    }

    public static int getNumberOfActiveCycleIdentifiers() {
        return activeCycleIdentifiers.size();
    }

    public static String getMissingInCycleVerifiers() {
        return missingInCycleVerifiers;
    }

    public static boolean ipAddressInCycle(ByteBuffer ipAddress) {
        return activeCycleIpAddresses.isEmpty() || activeCycleIpAddresses.contains(ipAddress);
    }

    public static int getNodeJoinRequestsSent() {
        return nodeJoinRequestsSent.get();
    }

    public static boolean connectedToMesh() {

        // When we request the node list from another node, it will add this node to the list. So, the minimum number
        // of nodes in a proper mesh is two.
        return ipAddressToNodeMap.size() > 1;
    }

    public static byte[] identifierForIpAddress(byte[] address) {

        byte[] identifier = null;
        if (address != null) {
            ByteBuffer addressBuffer = ByteBuffer.wrap(address);
            Node node = ipAddressToNodeMap.get(addressBuffer);
            if (node != null) {
                identifier = node.getIdentifier();
            }
        }

        return identifier;
    }

    public static byte[] identifierForIpAddress(String addressString) {

        return identifierForIpAddress(IpUtil.addressFromString(addressString));
    }

    public static void markFailedConnection(String addressString) {

        byte[] address = IpUtil.addressFromString(addressString);
        if (address != null && !ByteUtil.isAllZeros(address)) {
            ByteBuffer addressBuffer = ByteBuffer.wrap(address);
            Node node = ipAddressToNodeMap.get(addressBuffer);
            if (node != null) {
                node.markFailedConnection();
            }
        }
    }

    public static void markSuccessfulConnection(String addressString) {

        byte[] address = IpUtil.addressFromString(addressString);
        if (address != null) {
            ByteBuffer addressBuffer = ByteBuffer.wrap(address);
            Node node = ipAddressToNodeMap.get(addressBuffer);
            if (node != null) {
                node.markSuccessfulConnection();
            }
        }
    }

    public static void updateActiveVerifiersAndRemoveOldNodes() {

        Set<ByteBuffer> currentCycle = BlockManager.verifiersInCurrentCycleSet();

        Set<ByteBuffer> activeCycleIdentifiers = ConcurrentHashMap.newKeySet();
        Set<ByteBuffer> activeCycleIpAddresses = ConcurrentHashMap.newKeySet();
        long thresholdTimestamp = System.currentTimeMillis() - Block.blockDuration *
                BlockManager.currentCycleLength() * 2;
        for (ByteBuffer ipAddress : new HashSet<>(ipAddressToNodeMap.keySet())) {
            Node node = ipAddressToNodeMap.get(ipAddress);
            if (node.isActive()) {
                ByteBuffer identifierBuffer = ByteBuffer.wrap(node.getIdentifier());
                if (currentCycle.contains(identifierBuffer)) {
                    activeCycleIdentifiers.add(identifierBuffer);
                    activeCycleIpAddresses.add(ByteBuffer.wrap(node.getIpAddress()));
                }
            } else if (node.getInactiveTimestamp() < thresholdTimestamp) {
                ipAddressToNodeMap.remove(ipAddress);
                LogUtil.println("removed node " + NicknameManager.get(node.getIdentifier()) + " from mesh on " +
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

        NodeManager.activeCycleIdentifiers = activeCycleIdentifiers;
        NodeManager.activeCycleIpAddresses = activeCycleIpAddresses;
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

                    // This is the V2 node-join message.
                    Message nodeJoinMessage = new Message(MessageType.NodeJoinV2_43, new NodeJoinMessageV2());
                    Message.fetchTcp(IpUtil.addressAsString(ipAddressBuffer.array()), port, nodeJoinMessage,
                            new MessageCallback() {
                                @Override
                                public void responseReceived(Message message) {

                                    if (message != null && message.getContent() instanceof NodeJoinResponseV2) {

                                        updateNode(message);

                                        NodeJoinResponseV2 response = (NodeJoinResponseV2) message.getContent();
                                        NicknameManager.put(message.getSourceNodeIdentifier(), response.getNickname());
                                    }
                                }
                            });
                }
            }
        } catch (Exception ignored) { }
    }

    public static void reloadNodeJoinQueue() {

        // Once per cycle, but no more frequently than every 30 iterations, and only when the queue of node-join
        // requests is empty, request the mesh from an arbitrary node and enqueue a node-join request to every node
        // in that node's response, along with a request to every node currently in the map. This helps to ensure a
        // tightly integrated mesh, with all nodes being aware of all other nodes. This also periodically checks to
        // ensure that waiting nodes are still online, eventually removing them if they disappear for too long.
        if (meshRequestWait.decrementAndGet() <= 0 && nodeJoinRequestQueue.isEmpty()) {

            meshRequestWait.set(Math.max(BlockManager.currentCycleLength(), minimumMeshRequestInterval));

            // The request should only be made for in-cycle nodes (MeshRequest15).
            Message meshRequest = new Message(MessageType.MeshRequest15, null);
            Message.fetchFromRandomNode(meshRequest, new MessageCallback() {
                @Override
                public void responseReceived(Message message) {

                    if (message == null) {
                        // For a null response, set the mesh request wait to zero so that another attempt will be made
                        // on the next iteration.
                        meshRequestWait.set(0);
                    } else {
                        // Enqueue node-join requests to all nodes in the response.
                        MeshResponse response = (MeshResponse) message.getContent();
                        for (Node node : response.getMesh()) {
                            enqueueNodeJoinMessage(node.getIpAddress(), node.getPortTcp());
                        }

                        // Enqueue node-join requests to all nodes in the current map. This ensures that dead nodes are
                        // eventually removed. For the first two reloads, only request in-cycle nodes to promote
                        // in-cycle connectedness.
                        List<Node> existingNodes = meshRequestSuccessCount.get() < 2 ? getCycle() : getMesh();
                        for (Node node : existingNodes) {
                            enqueueNodeJoinMessage(node.getIpAddress(), node.getPortTcp());
                        }

                        // Enqueue node-join requests to all new verifiers waiting to be added. These nodes will be
                        // added to the map if they respond successfully to these requests.
                        for (ByteBuffer ipAddress : newNodeIpToPortMap.keySet()) {
                            enqueueNodeJoinMessage(ipAddress.array(), newNodeIpToPortMap.getOrDefault(ipAddress,
                                    MeshListener.standardPortTcp));
                        }
                        LogUtil.println("added " + newNodeIpToPortMap.size() + " new nodes to request queue");
                        newNodeIpToPortMap.clear();

                        LogUtil.println("reloaded node-join request queue; size is now " + nodeJoinRequestQueue.size());

                        // Increment the meshRequestSuccessCount value and set the haveNodeHistory flag if we have
                        // enough queries and the flag is not yet set.
                        if (meshRequestSuccessCount.getAndIncrement() > 4 && !haveNodeHistory) {
                            haveNodeHistory = true;
                            PersistentData.put(haveNodeHistoryKey, haveNodeHistory);
                        }
                    }
                }
            });
        }
    }

    public static void demoteInCycleNodes() {

        for (Node node : ipAddressToNodeMap.values()) {

            if (BlockManager.verifierInCurrentCycle(ByteBuffer.wrap(node.getIdentifier()))) {
                node.setQueueTimestamp(System.currentTimeMillis());
            }
        }
    }

    public static void persistNodes() {

        // Write the file to a temporary location.
        File temporaryFile = new File(nodeFile.getAbsolutePath() + "_temp");
        temporaryFile.delete();
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(temporaryFile));
            String separator = "";
            for (Node node : getMesh()) {
                writer.write(separator + ByteUtil.arrayAsStringWithDashes(node.getIdentifier()) + ":" +
                        IpUtil.addressAsString(node.getIpAddress()) + ":" +
                        node.getPortTcp() + ":" +
                        node.getPortUdp() + ":" +
                        node.getQueueTimestamp() + ":" +
                        "0:" +  // identifier-change timestamp; no longer used
                        node.getInactiveTimestamp());
                separator = "\n";
            }
        } catch (Exception ignored) { }

        // Close the temporary file.
        if (writer != null) {
            try {
                writer.close();
            } catch (Exception ignored) { }
        }

        // Move the temporary file to the new location.
        try {
            Files.move(Paths.get(temporaryFile.getAbsolutePath()), Paths.get(nodeFile.getAbsolutePath()),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) { }
    }

    private static void loadPersistedNodes() {

        // This method is called in the class's static block. We load the persisted nodes into the mesh map.
        Path path = Paths.get(nodeFile.getAbsolutePath());
        try {
            BufferedReader reader = new BufferedReader(new FileReader(nodeFile));
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    String[] split = line.split(":");
                    byte[] identifier = ByteUtil.byteArrayFromHexString(split[0], FieldByteSize.identifier);
                    byte[] ipAddress = IpUtil.addressFromString(split[1]);
                    int portTcp = Integer.parseInt(split[2]);
                    int portUdp = Integer.parseInt(split[3]);
                    long queueTimestamp = Long.parseLong(split[4]);
                    // long identifierChangeTimestamp = Long.parseLong(split[5]);  no longer used
                    long inactiveTimestamp = Long.parseLong(split[6]);

                    Node node = new Node(identifier, ipAddress, portTcp, portUdp);
                    node.setQueueTimestamp(queueTimestamp);
                    node.setInactiveTimestamp(inactiveTimestamp);

                    ipAddressToNodeMap.put(ByteBuffer.wrap(ipAddress), node);
                } catch (Exception ignored) { }
            }
            reader.close();
        } catch (Exception ignored) { }

        LogUtil.println("NodeManager initialization: loaded " + ipAddressToNodeMap.size() + " nodes into map");
    }
}
