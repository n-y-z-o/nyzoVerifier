package co.nyzo.verifier;

import co.nyzo.verifier.messages.NewVerifierVote;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class NewVerifierQueueManager {

    private static NewVerifierVote currentVote = null;

    public static synchronized void updateVote() {

        NewVerifierVote vote = calculateVote();

        if (vote != null && (currentVote == null ||
                !ByteUtil.arraysAreEqual(vote.getIdentifier(), currentVote.getIdentifier()) ||
                !ByteUtil.arraysAreEqual(vote.getIpAddress(), currentVote.getIpAddress()))) {

            currentVote = vote;
        }
    }

    private static synchronized NewVerifierVote calculateVote() {

        Set<ByteBuffer> currentCycle = BlockManager.verifiersInCurrentCycle();

        List<Node> mesh = NodeManager.getMesh();
        Node oldestNewVerifier = null;
        for (Node node : mesh) {
            //if (!currentCycle.contains(ByteBuffer.wrap(node.getIdentifier())) &&
            //        !) {
            //    newVerifiers.add(node);
            //}
        }

        return oldestNewVerifier == null ? null : new NewVerifierVote(oldestNewVerifier.getIdentifier(),
                oldestNewVerifier.getIpAddress());
    }
}
