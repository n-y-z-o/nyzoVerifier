package co.nyzo.verifier.client;

import co.nyzo.verifier.Node;
import co.nyzo.verifier.messages.MeshResponse;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

public class ClientNodeManager {

    // This is a much simpler class than the NodeManager class used by the verifier. Unlike the verifier, the client
    // will never have any voting rights in the cycle, and it will never send node joins to any other nodes.

    // The scoring system is used to assign a ranking on suspected probability of responding to messages. Also, when a
    // node reaches the minimum score, it is removed from both maps.
    private static final int minimumScore = 0;
    private static final int maximumScore = 40;
    private static final int successIncrement = 4;
    private static final int failureDecrement = -4;
    private static final int leakDecrement = -1;

    // The maximum size of the map is 10,000 nodes. This is a reasonable value that is far higher than it needs to be
    // but still a small impact on memory. It is exposed publicly for testing.
    public static final int maximumMapSize = 10000;

    private static Map<ByteBuffer, Node> ipAddressToNodeMap = new ConcurrentHashMap<>();
    private static Map<ByteBuffer, Integer> ipAddressToScoreMap = new ConcurrentHashMap<>();
    private static List<Node> preferredNodes = new ArrayList<>();
    private static List<Node> allNodes = new ArrayList<>();

    private static final BiFunction<Integer, Integer, Integer> mergeFunction =
            new BiFunction<Integer, Integer, Integer>() {
                @Override
                public Integer apply(Integer integer0, Integer integer1) {
                    int value0 = integer0 == null ? 0 : integer0;
                    int value1 = integer1 == null ? 0 : integer1;
                    return Math.min(Math.max(minimumScore, value0 + value1), maximumScore);
                }
            };

    public static void processMeshResponse(MeshResponse meshResponse) {

        if (meshResponse != null && meshResponse.getMesh() != null && !meshResponse.getMesh().isEmpty()) {
            // Leak away the score for all nodes. This causes verifiers that are only returned in a single mesh response
            // to eventually be removed, even if they are not queried.
            for (ByteBuffer ipAddress : ipAddressToScoreMap.keySet()) {
                ipAddressToScoreMap.merge(ipAddress, leakDecrement, mergeFunction);
            }

            // Process the nodes in the response.
            for (Node node : meshResponse.getMesh()) {
                ByteBuffer ipAddress = ByteBuffer.wrap(node.getIpAddress());
                ipAddressToNodeMap.put(ipAddress, node);
                ipAddressToScoreMap.merge(ipAddress, successIncrement, mergeFunction);  // Add to the node score.
            }
        }

        updateMapsAndLists();
    }

    public static void updateMapsAndLists() {

        // Remove nodes until the map is under the maximum size. Also remove all nodes with a score of 0.
        int removalScore = 0;
        while (ipAddressToNodeMap.size() > maximumMapSize || removalScore == 0) {
            Set<ByteBuffer> ipAddresses = new HashSet<>(ipAddressToNodeMap.keySet());
            Iterator<ByteBuffer> ipAddressIterator = ipAddresses.iterator();
            while (ipAddressIterator.hasNext() && (ipAddressToNodeMap.size() > maximumMapSize || removalScore == 0)) {
                ByteBuffer ipAddress = ipAddressIterator.next();
                if (ipAddressToScoreMap.getOrDefault(ipAddress, 0) <= removalScore) {
                    ipAddressToNodeMap.remove(ipAddress);
                    ipAddressToScoreMap.remove(ipAddress);
                }
            }
            removalScore++;
        }

        // Remove all scores that represent nodes not present in the node map. Presence of stray scores in this map is
        // unlikely outside contrived testing scenarios, but this operation is still prudent to ensure consistency.
        for (ByteBuffer ipAddress : new HashSet<>(ipAddressToScoreMap.keySet())) {
            if (!ipAddressToNodeMap.containsKey(ipAddress)) {
                ipAddressToScoreMap.remove(ipAddress);
            }
        }

        // Calculate the sum of all scores.
        int[] scoreSums = new int[maximumScore - minimumScore + 1];
        int scoreSum = 0;
        for (Integer score : ipAddressToScoreMap.values()) {
            scoreSums[score - minimumScore] += score;
            scoreSum += score;
        }

        // Now, calculate the cutoff score for preferred nodes. The cutoff score is the highest score for
        // which the all scores greater than or equal have a sum that is at least 50% of the sum of all scores.
        int cutoffScore = maximumScore;
        int aboveCutoffSum = scoreSums[cutoffScore - minimumScore];
        while (aboveCutoffSum < scoreSum / 2) {
            cutoffScore--;
            aboveCutoffSum += scoreSums[cutoffScore - minimumScore];
        }

        // Build the lists of all and preferred nodes. These are lists to allow easy selection of a random element.
        // Build new lists and swap references to provide atomic swaps.
        List<Node> preferredNodes = new ArrayList<>();
        List<Node> allNodes = new ArrayList<>();
        for (ByteBuffer ipAddress : ipAddressToNodeMap.keySet()) {
            int score = ipAddressToScoreMap.getOrDefault(ipAddress, 0);
            Node node = ipAddressToNodeMap.get(ipAddress);
            if (node != null) {  // check to ensure thread safety
                if (score >= cutoffScore) {
                    preferredNodes.add(node);
                }
                allNodes.add(node);
            }
        }
        ClientNodeManager.preferredNodes = preferredNodes;
        ClientNodeManager.allNodes = allNodes;
    }

    public static void markSuccess(Node node) {
        ByteBuffer ipAddress = ByteBuffer.wrap(node.getIpAddress());
        ipAddressToScoreMap.merge(ipAddress, successIncrement, mergeFunction);  // Add to the node score.
    }

    public static void markFailure(Node node) {
        ByteBuffer ipAddress = ByteBuffer.wrap(node.getIpAddress());
        ipAddressToScoreMap.merge(ipAddress, failureDecrement, mergeFunction);  // Subtract from the node score.
    }

    public static Node randomNode() {
        List<Node> allNodes = ClientNodeManager.allNodes;  // Get a local reference for thread safety.
        return allNodes.isEmpty() ? null : allNodes.get(new Random().nextInt(allNodes.size()));
    }

    public static Node randomPreferredNode() {
        List<Node> preferredNodes = ClientNodeManager.preferredNodes;  // Get a local reference for thread safety.
        return preferredNodes.isEmpty() ? null : preferredNodes.get(new Random().nextInt(preferredNodes.size()));
    }

    public static Collection<Node> getMesh() {
        return ipAddressToNodeMap.values();
    }

    public static int getNumberOfNodesInMesh() {
        return allNodes.size();
    }

    public static int getNumberOfPreferredNodesInMesh() {
        return preferredNodes.size();
    }
}
