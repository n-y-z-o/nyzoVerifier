package co.nyzo.verifier;

import co.nyzo.verifier.messages.NewVerifierVote;
import co.nyzo.verifier.util.NotificationUtil;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

public class NewVerifierQueueManager {

    private static int consecutiveBlocksVotingForSameVerifier = 0;

    private static ByteBuffer currentVote = null;

    public static synchronized void updateVote() {

        ByteBuffer vote = calculateVote();

        // Only update if the new vote is not null and the current vote is different than the new vote.
        if (vote != null && !vote.equals(currentVote)) {

            // Wrap the vote and register it locally.
            NewVerifierVote wrappedVote = new NewVerifierVote(vote.array());
            NewVerifierVoteManager.registerVote(Verifier.getIdentifier(), wrappedVote, true);

            // Store the current vote and reset the counter.
            currentVote = vote;
            consecutiveBlocksVotingForSameVerifier = 1;

            // If this verifier has voting power, broadcast the vote.
            if (Verifier.inCycle()) {
                Message message = new Message(MessageType.NewVerifierVote21, wrappedVote);
                Message.broadcast(message);
            }

        } else if (vote != null && vote.equals(currentVote)) {

            // In this case, our vote has not changed. Increment the counter. Demote the node if it has been at the top
            // of the list too long. The calculation typically gives the node 50 blocks to join once it has received
            // our vote.
            consecutiveBlocksVotingForSameVerifier++;
            if (BlockManager.isCycleComplete() &&
                    consecutiveBlocksVotingForSameVerifier > BlockManager.currentCycleLength() * 2 + 3 + 50) {
                NodeManager.demoteIdentifier(vote.array());
                NodeManager.persistQueueTimestamps();
            }
        }
    }

    private static synchronized ByteBuffer calculateVote() {

        Set<ByteBuffer> currentCycle = BlockManager.verifiersInCurrentCycleSet();

        // Find the verifier that has been on the mesh longest but is not in the current cycle.
        List<Node> mesh = NodeManager.getMesh();
        Node oldestNewVerifier = null;
        for (Node node : mesh) {
            if (node.isActive() && !currentCycle.contains(ByteBuffer.wrap(node.getIdentifier()))) {
                if (oldestNewVerifier == null || node.getQueueTimestamp() < oldestNewVerifier.getQueueTimestamp()) {
                    oldestNewVerifier = node;
                }
            }
        }

        // Wrap the identifier in a buffer to get ready to check for the override.
        ByteBuffer result = oldestNewVerifier == null ? null : ByteBuffer.wrap(oldestNewVerifier.getIdentifier());

        // If the override is not all zeros (it is never null), check if it is in the current cycle. If the override
        // vote is in the current cycle, remove it (set it to all zeros). Otherwise, use it. If you really think out
        // this code, there is a race condition where we might check an old override, a new override could be set, and
        // then we might erase the new override. This is ***extremely** unlikely, though, and not a serious issue if
        // it does happen (the override would not stick, and it would need to be sent again). So, we won't bother
        // addressing it.
        byte[] overrideIdentifier = NewVerifierVoteManager.getOverride();
        if (!ByteUtil.isAllZeros(overrideIdentifier)) {
            ByteBuffer overrideBuffer = ByteBuffer.wrap(overrideIdentifier);
            if (BlockManager.verifierInCurrentCycle(overrideBuffer)) {
                NewVerifierVoteManager.setOverride(new byte[FieldByteSize.identifier]);  // erase the override
            } else {
                result = overrideBuffer;  // use the override
            }
        }

        return result;
    }

    public static ByteBuffer getCurrentVote() {
        return currentVote;
    }
}
