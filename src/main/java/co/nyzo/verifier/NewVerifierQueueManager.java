package co.nyzo.verifier;

import co.nyzo.verifier.messages.NewVerifierVote;
import co.nyzo.verifier.util.NotificationUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

public class NewVerifierQueueManager {

    private static ByteBuffer currentVote = null;

    public static synchronized void updateVote() {

        ByteBuffer vote = calculateVote();

        // Only update if the new vote is not null and the current vote is either null or different than the new vote.
        if (vote != null && (currentVote == null || !vote.equals(currentVote))) {

            Message message = new Message(MessageType.NewVerifierVote21, new NewVerifierVote(vote.array()));
            Message.broadcast(message);
            NotificationUtil.send("sent vote for verifier " + NicknameManager.get(vote.array()));

            currentVote = vote;
        }
    }

    private static synchronized ByteBuffer calculateVote() {

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

        return oldestNewVerifier == null ? null : ByteBuffer.wrap(oldestNewVerifier.getIdentifier());
    }
}
