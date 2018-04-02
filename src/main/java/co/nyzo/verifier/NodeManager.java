package co.nyzo.verifier;

import co.nyzo.verifier.messages.NodeJoinMessage;
import co.nyzo.verifier.messages.NodeListResponse;
import co.nyzo.verifier.util.IpUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class NodeManager {

    // TODO: work out the details of multiple verifiers at a single IP address

    private static final List<Node> queue = new ArrayList<>();
    private static final Map<Integer, Node> ipAddressToNodeMap = new HashMap<>();
    private static final Map<ByteBuffer, Node> identifierToNodeMap = new HashMap<>();

    // full node: a node that accepts incoming connections
    // client node: a node that does not accept incoming connections

    // When a node joins the network, it broadcasts a node-has-joined message directly to every full node in the mesh.
    // The node-has-joined message contains only the identifier that the verifier wants to associate with the node.
    // The full node stores the timestamp, the IP address, and the identifier.

    private static final Comparator<Node> ascendingTimestampComparator = new Comparator<Node>() {
        @Override
        public int compare(Node node1, Node node2) {
            return ((Long) node1.getQueueTimestamp()).compareTo(node2.getQueueTimestamp());
        }
    };

    public static void updateNode(byte[] identifier, byte[] ipAddress, int port, boolean fullNode) {

        updateNode(identifier, ipAddress, port, fullNode, 0);
    }

    public static synchronized void updateNode(byte[] identifier, byte[] ipAddress, int port, boolean fullNode,
                                               long queueTimestamp) {

        if (identifier != null && identifier.length == FieldByteSize.identifier && ipAddress != null &&
                ipAddress.length == FieldByteSize.ipAddress && port > 0) {
            ByteBuffer identifierByteBuffer = ByteBuffer.wrap(identifier);

            // Get the existing node from the map.
            int ipAddressAsInt = IpUtil.addressAsInt(ipAddress);
            Node existingNodeForIp = ipAddressToNodeMap.get(ipAddressAsInt);
            Node existingNodeForIdentifier = identifierToNodeMap.get(identifierByteBuffer);

            // To enforce the requirements that each IP may be in the queue only once and each identifier may be in the
            // queue only once:
            // (1) If no node was returned for either lookup, create a new node.
            // (2) If a node was returned for IP but not identifier, update the identifier and port with the IP.
            // (3) If a node was returned for identifier but not IP, update the IP and port with the identifier.
            // (4) If the node returned for both was the same, update the port.
            // (5) If a different node was returned for each, remove the lower-ranked node and update the other.
            if (existingNodeForIp == null && existingNodeForIdentifier == null) {
                Node newNode = new Node(identifier, ipAddress, port, fullNode);
                int queueIndex = -1;
                queue.add(newNode);
                ipAddressToNodeMap.put(ipAddressAsInt, newNode);
                identifierToNodeMap.put(identifierByteBuffer, newNode);

                // A timestamp is only provided when fetching a node list from another node. Those should already be
                // deduped; if they are not, then simply choosing the first timestamp encountered for each IP and
                // identifier is a reasonable way to clean up the data.
                if (queueTimestamp > 0) {
                    newNode.setQueueTimestamp(queueTimestamp);
                }
            } else if (existingNodeForIdentifier == null) {
                existingNodeForIp.setIdentifier(identifier);
                existingNodeForIp.setPort(port);
                identifierToNodeMap.put(identifierByteBuffer, existingNodeForIp);
            } else if (existingNodeForIp == null) {
                existingNodeForIdentifier.setIpAddress(ipAddress);
                existingNodeForIdentifier.setPort(port);
                ipAddressToNodeMap.put(ipAddressAsInt, existingNodeForIdentifier);
            } else if (existingNodeForIp == existingNodeForIdentifier) {
                existingNodeForIp.setPort(port);
            } else {  // found two different nodes
                if (existingNodeForIp.getQueueTimestamp() < existingNodeForIdentifier.getQueueTimestamp()) {
                    queue.remove(existingNodeForIdentifier);
                    existingNodeForIp.setIdentifier(identifier);
                    existingNodeForIp.setPort(port);
                    identifierToNodeMap.put(identifierByteBuffer, existingNodeForIp);
                } else {
                    queue.remove(existingNodeForIp);
                    existingNodeForIdentifier.setIpAddress(ipAddress);
                    existingNodeForIdentifier.setPort(port);
                    ipAddressToNodeMap.put(ipAddressAsInt, existingNodeForIdentifier);
                }
            }
        }
    }

    public static void fetchNodeList() {

        System.out.println("fetching node list");
        Message.fetch("nyzo.co", MeshListener.standardPort, new Message(MessageType.NodeListRequest1, null), false,
                new MessageCallback() {
                    @Override
                    public void responseReceived(Message message) {
                        System.out.println("return message in fetchNodeList is " + message);
                        List<Node> nodes = new ArrayList<>();
                        try {
                            if (message != null) {
                                NodeListResponse response = (NodeListResponse) message.getContent();
                                nodes = response.getNodes();
                            }
                        } catch (Exception ignored) { }

                        if (!nodes.isEmpty()) {
                            // Ensure the list is sorted and add the nodes to the local list.
                            for (Node node : nodes) {
                                updateNode(node.getIdentifier(), node.getIpAddress(), node.getPort(), node.isFullNode(),
                                        node.getQueueTimestamp());
                            }
                        }

                        // If connected to the mesh, send node-join messages to all full nodes. Otherwise, wait 10
                        // seconds and retry.
                        if (connectedToMesh()) {
                            List<Node> queue = getQueue();
                            for (Node node : queue) {
                                if (node.isFullNode()) {
                                    Message nodeJoinMessage = new Message(MessageType.NodeJoin3,
                                            new NodeJoinMessage(MeshListener.getPort(), true));
                                    Message.fetch(IpUtil.addressAsString(node.getIpAddress()), node.getPort(),
                                            nodeJoinMessage, true, null);
                                }
                            }
                        } else {
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        Thread.sleep(10000L);
                                    } catch (Exception ignored) { }
                                    fetchNodeList();
                                }
                            }).start();
                        }
                    }
                });
    }

    public static synchronized List<Node> getQueue() {
        return new ArrayList<>(queue);
    }

    public static boolean connectedToMesh() {

        // TODO: make sure that we have all the data necessary to run as a proper verifier here
        // TODO: this will include making sure we have a recent history of blocks

        // One node will be the verifier. If any more are present, we are connected to the mesh.
        return queue.size() > 1;
    }
}
