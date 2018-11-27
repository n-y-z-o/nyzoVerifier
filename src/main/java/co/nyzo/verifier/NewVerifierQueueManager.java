package co.nyzo.verifier;

import co.nyzo.verifier.messages.NewVerifierVote;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

public class NewVerifierQueueManager {

    private static int consecutiveBlocksVotingForTopVerifier = 0;

    private static ByteBuffer currentVote = null;

    public static synchronized void updateVote() {

        ByteBuffer vote = calculateVote();

        if (vote != null) {

            // If the vote has changed, register and broadcast, if necessary.
            if (!vote.equals(currentVote)) {

                // Wrap the vote and register it locally.
                NewVerifierVote wrappedVote = new NewVerifierVote(vote.array());
                NewVerifierVoteManager.registerVote(Verifier.getIdentifier(), wrappedVote, true);

                // Store the current vote.
                currentVote = vote;

                // If this verifier has voting power, broadcast the vote.
                if (Verifier.inCycle()) {
                    Message message = new Message(MessageType.NewVerifierVote21, wrappedVote);
                    Message.broadcast(message);
                }
            }

            // If this is the top-voted verifier, increment a counter. If the counter has exceeded more than 50 more
            // than the minimum new-verifier interval, demote the identifier so a new vote will be cast in the next
            // verifier iteration. This prevents the automatic voting process from keeping a verifier at the top of
            // the new-verifier list indefinitely. This demotion will not have any effect on a manual override vote.
            if (NewVerifierVoteManager.topVerifiers().indexOf(currentVote) == 0) {

                consecutiveBlocksVotingForTopVerifier++;
                if (BlockManager.isCycleComplete() &&
                        consecutiveBlocksVotingForTopVerifier > BlockManager.currentCycleLength() * 2 + 3 + 50) {
                    NodeManager.demoteIdentifier(vote.array());
                    NodeManager.persistQueueTimestamps();
                }
            } else {
                consecutiveBlocksVotingForTopVerifier = 0;
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
