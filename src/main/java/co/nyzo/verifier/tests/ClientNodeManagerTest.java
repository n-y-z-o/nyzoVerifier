package co.nyzo.verifier.tests;

import co.nyzo.verifier.MeshListener;
import co.nyzo.verifier.Node;
import co.nyzo.verifier.RunMode;
import co.nyzo.verifier.client.ClientNodeManager;
import co.nyzo.verifier.messages.MeshResponse;

import java.util.*;

public class ClientNodeManagerTest implements NyzoTest {

    private String failureCause = null;
    private static int batchSize = 1000;
    private static int numberOfBatches = 5;

    public static void main(String[] args) {

        RunMode.setRunMode(RunMode.Test);
        ClientNodeManagerTest test = new ClientNodeManagerTest();
        boolean successful = test.run();

        if (!successful) {
            System.out.println(TestUtil.failureCause(test.getFailureCause()));
        }
    }

    public boolean run() {

        // Perform the initial round of adding nodes. The first batch falls out after four iterations. Recent batches
        // have higher scores and cluster as preferred.
        boolean successful = true;
        for (int i = 0; i < numberOfBatches && successful; i++) {
            int expectedTotal = Math.min(batchSize * Math.min(i + 1, 4), ClientNodeManager.maximumMapSize);
            int expectedPreferred = i < 2 ? batchSize : batchSize * 2;
            successful = addNodes(i, batchSize, expectedTotal, expectedPreferred, "initial round");
        }

        // Perform the next round of adding nodes. The preferred list always contains two recent batches.
        for (int i = 0; i < numberOfBatches && successful; i++) {
            int expectedTotal = Math.min(batchSize * Math.min(numberOfBatches, 4), ClientNodeManager.maximumMapSize);
            int expectedPreferred = batchSize * 2;
            successful = addNodes(i, batchSize, expectedTotal, expectedPreferred, "second round");
        }

        // Mark successes on the lowest-scored batch to cause it to first become preferred then to cause it to be the
        // only preferred batch.
        for (int i = 0; i < 2 && successful; i++) {
            int expectedTotal = Math.min(batchSize * Math.min(numberOfBatches, 4), ClientNodeManager.maximumMapSize);
            int expectedPreferred = i == 0 ? batchSize * 2 : batchSize;
            successful = markSuccesses(numberOfBatches - 4, expectedTotal, expectedPreferred,
                    "marking batch success, iteration " + i);
        }

        // Mark failures on the last batch to have it removed.
        if (successful) {
            int expectedTotal = Math.min(batchSize * Math.min(numberOfBatches, 3), ClientNodeManager.maximumMapSize);
            int expectedPreferred = Math.min(batchSize, ClientNodeManager.maximumMapSize);
            successful = markFailures(numberOfBatches - 1, expectedTotal, expectedPreferred, "last batch failure");
        }

        // Add a single large batch to fill the map. Adding small batches would never reach the maximum due to score
        // leakage.
        if (successful) {
            int expectedTotal = ClientNodeManager.maximumMapSize;
            int expectedPreferred = ClientNodeManager.maximumMapSize;
            successful = addNodes(0, ClientNodeManager.maximumMapSize, expectedTotal, expectedPreferred,
                    "filling map to maximum");
        }

        System.out.println(TestUtil.passFail(successful));

        return successful;
    }

    private boolean addNodes(int tag, int batchSize, int expectedTotal, int expectedPreferred, String label) {

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

        // Update the maps and the lists. This happens automatically when a mesh response is processed, but it must be
        // done manually here to reflect the effects of the successes.
        ClientNodeManager.updateMapsAndLists();

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

        // Update the maps and the lists. This happens automatically when a mesh response is processed, but it must be
        // done manually here to reflect the effects of the failures.
        ClientNodeManager.updateMapsAndLists();

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
