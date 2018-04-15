package co.nyzo.verifier;

import co.nyzo.verifier.messages.NodeJoinMessage;
import co.nyzo.verifier.messages.NodeJoinResponse;
import co.nyzo.verifier.messages.NodeListResponse;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class NodeManager {

    public static void main(String[] args) {

        // This is a simple test of fetching the node list.
        fetchNodeList(0);
    }

    private static final int maximumNumberOfSeedVerifiers = 10;

    // TODO: work out the details of multiple verifiers at a single IP address

    private static final List<Node> nodePool = new ArrayList<>();
    private static final Map<Integer, Node> ipAddressToNodeMap = new HashMap<>();
    private static final Map<ByteBuffer, Node> identifierToNodeMap = new HashMap<>();

    // full node: a node that accepts incoming connections
    // client node: a node that does not accept incoming connections

    // When a node joins the network, it broadcasts a node-has-joined message directly to every full node in the mesh.
    // The node-has-joined message contains only the identifier that the verifier wants to associate with the node.
    // The full node stores the timestamp, the IP address, and the identifier.

    public static void updateNode(byte[] identifier, byte[] ipAddress, int port, boolean fullNode) {

        updateNode(identifier, ipAddress, port, fullNode, 0);
    }

    public static synchronized void updateNode(byte[] identifier, byte[] ipAddress, int port, boolean fullNode,
                                               long queueTimestamp) {

        System.out.println("adding node " + ByteUtil.arrayAsStringWithDashes(identifier) + ", " +
                IpUtil.addressAsString(ipAddress));

        if (identifier != null && identifier.length == FieldByteSize.identifier && ipAddress != null &&
                ipAddress.length == FieldByteSize.ipAddress) {

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
                nodePool.add(newNode);
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
                    nodePool.remove(existingNodeForIdentifier);
                    existingNodeForIp.setIdentifier(identifier);
                    existingNodeForIp.setPort(port);
                    identifierToNodeMap.put(identifierByteBuffer, existingNodeForIp);
                } else {
                    nodePool.remove(existingNodeForIp);
                    existingNodeForIdentifier.setIpAddress(ipAddress);
                    existingNodeForIdentifier.setPort(port);
                    ipAddressToNodeMap.put(ipAddressAsInt, existingNodeForIdentifier);
                }
            }
        }
    }

    public static void fetchNodeList(int index) {

        String url = "verifier" + index + ".nyzo.co";
        System.out.println("fetching node list from " + url);
        Message.fetch(url, MeshListener.standardPort, new Message(MessageType.NodeListRequest1, null), false,
                new MessageCallback() {
                    @Override
                    public void responseReceived(Message message) {
                        List<Node> nodes = new ArrayList<>();
                        try {
                            if (message != null) {
                                NodeListResponse response = (NodeListResponse) message.getContent();
                                nodes = response.getNodes();
                            }
                        } catch (Exception ignored) { }

                        // Add the nodes to the local list.
                        for (Node node : nodes) {
                            updateNode(node.getIdentifier(), node.getIpAddress(), node.getPort(), node.isFullNode(),
                                    node.getQueueTimestamp());
                        }

                        // If we got nodes in the response, send node-join messages to all full nodes and fetch the
                        // current transaction pool. Otherwise, wait 10 seconds and retry.
                        if (!nodes.isEmpty()) {
                            List<Node> nodePool = getMesh();
                            for (Node node : nodePool) {
                                if (node.isFullNode()) {
                                    Message nodeJoinMessage = new Message(MessageType.NodeJoin3,
                                            new NodeJoinMessage(MeshListener.getPort(), true));
                                    System.out.println("sending node-join message to " + nodeJoinMessage);
                                    Message.fetch(IpUtil.addressAsString(node.getIpAddress()), node.getPort(),
                                            nodeJoinMessage, true, new MessageCallback() {
                                                @Override
                                                public void responseReceived(Message message) {
                                                    System.out.println("received node join response " + message);
                                                    NodeJoinResponse response = (NodeJoinResponse) message.getContent();
                                                    ChainInitializationManager.processNodeJoinResponse(response);
                                                }
                                            });
                                }
                            }

                            // If this node is not yet in the pool, re-fetch. This typically means that the entire
                            // process (fetching, broadcasting join, getting the transaction pool) is done twice, but
                            // the redundancy is not a problem and may actually help in some cases.
                            if (!identifierToNodeMap.containsKey(ByteBuffer.wrap(Verifier.getIdentifier()))) {
                                System.out.println("need to re-fetch node pool");
                                fetchNodeList(0);
                            }

                            TransactionPool.fetchFromMesh();
                        } else {
                            if (!UpdateUtil.shouldTerminate()) {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            Thread.sleep(10000L);
                                        } catch (Exception ignored) {
                                        }
                                        fetchNodeList((index + 1) % maximumNumberOfSeedVerifiers);
                                    }
                                }, "NpdeManager-fetchNodeListRetry").start();
                            }
                        }
                    }
                });
    }

    public static synchronized List<Node> getMesh() {
        return new ArrayList<>(nodePool);
    }

    public static boolean connectedToMesh() {

        // When we request the node list from another node, it will add this node to the list. So, the minimum number
        // of nodes in a proper mesh is two.
        return nodePool.size() > 1;
    }
}
