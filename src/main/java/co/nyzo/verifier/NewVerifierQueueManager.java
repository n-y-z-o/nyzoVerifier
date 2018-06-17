package co.nyzo.verifier;

import co.nyzo.verifier.messages.NewVerifierVote;
import co.nyzo.verifier.util.NotificationUtil;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

public class NewVerifierQueueManager {

    private static NewVerifierVote currentVote = null;

    public static synchronized void updateVote() {

        NewVerifierVote vote = calculateVote();

        // Only update if the new vote is not null and the current vote is either null or different than the new vote.
        if (vote != null && (currentVote == null ||
                !ByteUtil.arraysAreEqual(vote.getIdentifier(), currentVote.getIdentifier()) ||
                !ByteUtil.arraysAreEqual(vote.getIpAddress(), currentVote.getIpAddress()))) {

            Message message = new Message(MessageType.NewVerifierVote21, vote);
            Message.broadcast(message);
            NotificationUtil.sendOnce("sent vote for verifier " + vote.getIdentifier());

            currentVote = vote;
        }
    }

    private static synchronized NewVerifierVote calculateVote() {

        Set<ByteBuffer> currentCycle = BlockManager.verifiersInCurrentCycle();

        List<Node> mesh = NodeManager.getMesh();
        Node oldestNewVerifier = null;
        for (Node node : mesh) {
            if (node.isActive() && !currentCycle.contains(ByteBuffer.wrap(node.getIdentifier()))) {
                if (oldestNewVerifier == null || node.getQueueTimestamp() < oldestNewVerifier.getQueueTimestamp()) {
                    oldestNewVerifier = node;
                }
            }
        }

        return oldestNewVerifier == null ? null : new NewVerifierVote(oldestNewVerifier.getIdentifier(),
                oldestNewVerifier.getIpAddress());
    }
}
