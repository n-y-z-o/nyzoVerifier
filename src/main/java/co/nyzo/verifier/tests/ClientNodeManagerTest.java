package co.nyzo.verifier.tests;

import co.nyzo.verifier.MeshListener;
import co.nyzo.verifier.Node;
import co.nyzo.verifier.client.ClientNodeManager;
import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.messages.MeshResponse;

import java.util.*;

public class ClientNodeManagerTest implements NyzoTest {

    private String failureCause = null;
    private static int batchSize = 1000;
    private static int numberOfBatches = 5;

    public static void main(String[] args) {

        ClientNodeManagerTest test = new ClientNodeManagerTest();
        boolean successful = test.run();

        if (!successful) {
            System.out.println(TestUtil.failureCause(test.getFailureCause()));
        }
    }

    public boolean run() {

        // Perform the initial round of adding nodes. The full list and preferred list increase in size as each batch is
        // added.
        boolean successful = true;
        for (int i = 0; i < numberOfBatches && successful; i++) {
            int expectedCount = Math.min(batchSize * (i + 1), ClientNodeManager.maximumMapSize);
            successful = addNodes(i, expectedCount, expectedCount, "initial round");
        }

        // Perform the next round of adding nodes. The preferred list changes in size according to the score
        // distribution.
        for (int i = 0; i < numberOfBatches && successful; i++) {
            int expectedTotal = Math.min(batchSize * numberOfBatches, ClientNodeManager.maximumMapSize);
            int secondRoundSum = batchSize * (i + 1) * 2;
            int firstRoundSum = batchSize * (numberOfBatches - i - 1);
            int expectedPreferred = Math.min(secondRoundSum >= (firstRoundSum + secondRoundSum) / 2 ? batchSize *
                    (i + 1) : batchSize * numberOfBatches, ClientNodeManager.maximumMapSize);
            successful = addNodes(i, expectedTotal, expectedPreferred, "second round");
        }

        // Mark successes on the first batch. This will affect the preferred list size when enough successes have been
        // tallied to lift this batch to preferred status over the other nodes.
        int preferredBatchSum = batchSize * 2;
        for (int i = 0; i < numberOfBatches + 1 && successful; i++) {
            int expectedTotal = Math.min(batchSize * numberOfBatches, ClientNodeManager.maximumMapSize);
            int expectedPreferred = i < numberOfBatches ? batchSize * numberOfBatches : batchSize;
            successful = markSuccesses(0, expectedTotal, expectedPreferred, "first batch success, iteration " + i);
        }

        // Mark failures on the last batch. After two failures, they will be removed.
        for (int i = 0; i < 2 && successful; i++) {
            int expectedTotal = Math.min(i == 0 ? batchSize * numberOfBatches : batchSize * (numberOfBatches - 1),
                    ClientNodeManager.maximumMapSize);
            int expectedPreferred = Math.min(batchSize, ClientNodeManager.maximumMapSize);
            successful = markFailures(numberOfBatches - 1, expectedTotal, expectedPreferred,
                    "last batch failure, iteration " + i);
        }

        // Add more batches until the maximum number of nodes is reached.
        int index = numberOfBatches;
        for (int i = numberOfBatches; ClientNodeManager.getNumberOfNodesInMesh() < ClientNodeManager.maximumMapSize &&
                successful; i++) {
            int expectedTotal = Math.min(batchSize * i, ClientNodeManager.maximumMapSize);
            int expectedPreferred;
            if (i < numberOfBatches + 2) {
                expectedPreferred = batchSize;
            } else if (i < numberOfBatches * 4 - 1) {
                expectedPreferred = batchSize * (numberOfBatches - 1);
            } else {
                expectedPreferred = batchSize * i;
            }
            successful = addNodes(i, expectedTotal, expectedPreferred, "filling map to maximum");
        }

        System.out.println(TestUtil.passFail(successful));

        return successful;
    }

    private boolean addNodes(int tag, int expectedTotal, int expectedPreferred, String label) {

        // Make and process a mesh response.
        List<Node> mesh = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            mesh.add(nodeWithTagAndIndex(tag, i));
        }
        ClientNodeManager.processMeshResponse(new MeshResponse(mesh));

        // Check the number of nodes.
        boolean successful = true;
        if (ClientNodeManager.getNumberOfNodesInMesh() != expectedTotal) {
            successful = false;
            failureCause = "adding nodes with tag " + tag + " (" + label + "), expected total=" +
                    expectedTotal + ", actual=" + ClientNodeManager.getNumberOfNodesInMesh();
        }

        // Check the number of preferred nodes. This should be all nodes, as there are no score differences to
        // separate the nodes.
        if (successful && ClientNodeManager.getNumberOfPreferredNodesInMesh() != expectedPreferred) {
            successful = false;
            failureCause = "adding nodes with tag " + tag + " (" + label + "), expected preferred=" +
                    expectedPreferred + ", actual=" + ClientNodeManager.getNumberOfPreferredNodesInMesh();
        }

        return successful;
    }

    private boolean markSuccesses(int tag, int expectedTotal, int expectedPreferred, String label) {

        // Mark successes for each node in the first batch.
        for (int i = 0; i < batchSize; i++) {
            ClientNodeManager.markSuccess(nodeWithTagAndIndex(tag, i));
        }

        // Process an empty mesh response to update the preferred count.
        ClientNodeManager.processMeshResponse(new MeshResponse(new ArrayList<>()));

        // Check the number of nodes.
        boolean successful = true;
        if (ClientNodeManager.getNumberOfNodesInMesh() != expectedTotal) {
            successful = false;
            failureCause = "marking successes with tag " + tag + " (" + label + "), expected total=" + expectedTotal +
                    ", actual=" + ClientNodeManager.getNumberOfNodesInMesh();
        }

        // Check the number of preferred nodes.
        if (successful && ClientNodeManager.getNumberOfPreferredNodesInMesh() != expectedPreferred) {
            successful = false;
            failureCause = "marking successes with tag " + tag + " (" + label + "), expected preferred=" +
                    expectedPreferred + ", actual=" + ClientNodeManager.getNumberOfPreferredNodesInMesh();
        }

        return successful;
    }

    private boolean markFailures(int tag, int expectedTotal, int expectedPreferred, String label) {

        // Mark failures for each node in the last batch.
        for (int i = 0; i < batchSize; i++) {
            ClientNodeManager.markFailure(nodeWithTagAndIndex(tag, i));
        }

        // Process an empty mesh response to update the preferred count.
        ClientNodeManager.processMeshResponse(new MeshResponse(new ArrayList<>()));

        // Check the number of nodes.
        boolean successful = true;
        if (ClientNodeManager.getNumberOfNodesInMesh() != expectedTotal) {
            successful = false;
            failureCause = "marking failures (" + label + "), expected total=" + expectedTotal + ", actual=" +
                    ClientNodeManager.getNumberOfNodesInMesh();
        }

        // Check the number of preferred nodes.
        if (successful && ClientNodeManager.getNumberOfPreferredNodesInMesh() != expectedPreferred) {
            successful = false;
            failureCause = "marking failures (" + label + "), expected preferred=" + expectedPreferred + ", actual=" +
                    ClientNodeManager.getNumberOfPreferredNodesInMesh();
        }

        return successful;
    }

    private static Node nodeWithTagAndIndex(int tag, int index) {
        byte[] identifier = new byte[] { (byte) tag, (byte) (index / 100), (byte) (index % 100) };
        byte[] ipAddress = new byte[] { (byte) tag, 0, (byte) (index / 100), (byte) (index % 100) };
        return new Node(identifier, ipAddress, MeshListener.standardPortTcp, MeshListener.standardPortUdp);
    }

    public String getFailureCause() {
        return failureCause;
    }
}
