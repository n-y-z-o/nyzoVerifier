package co.nyzo.verifier;

import co.nyzo.verifier.messages.NewVerifierVote;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PreferencesUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class NewVerifierQueueManager {

    private static final String autovoteMethodKey = "autovote";
    private static final String autovoteMethodValueFifo = "fifo";
    private static final String autovoteMethodValueLottery = "lottery";

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

                // Store the new vote and the previous vote in arrays. These will be used if we transmit the vote.
                byte[] newVote = vote.array();
                byte[] previousVote = currentVote == null ? new byte[FieldByteSize.identifier] : currentVote.array();

                // Store the current vote.
                currentVote = vote;

                // If this verifier has voting power, broadcast the vote.
                if (Verifier.inCycle()) {
                    Message message = new Message(MessageType.NewVerifierVote21, wrappedVote);
                    Message.broadcast(message);

                    // Also send the message to the verifier for which we are voting and the verifier for which we were
                    // previously voting. Most out-of-cycle verifiers do not need to know the vote tally, because they
                    // do not have voting power. However, informing the out-of-cycle subjects of these votes changes is
                    // helpful so they know when they should be producing and transmitting blocks.
                    List<Node> mesh = NodeManager.getMesh();
                    for (Node node : mesh) {
                        if (ByteUtil.arraysAreEqual(node.getIdentifier(), newVote) ||
                                ByteUtil.arraysAreEqual(node.getIdentifier(), previousVote)) {
                            Message.fetch(node, message, null);
                        }
                    }
                }
            }

            // If this is the top-voted verifier, increment a counter. If the counter has exceeded more than 50 more
            // than the minimum new-verifier interval, demote the identifier so a new vote will be cast in the next
            // verifier iteration. This prevents the automatic voting process from keeping a verifier at the top of
            // the new-verifier list indefinitely. This demotion will not have any effect on a manual override vote.
            // Note that this logic does not apply when the lottery logic is used, as the lottery switches votes every
            // 50 blocks.
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

        ByteBuffer vote;
        String autovotePreference = PreferencesUtil.get(autovoteMethodKey);
        if (autovotePreference.equals(autovoteMethodValueFifo)) {
            vote = calculateVoteFifoMethod();
        } else {  // default: autovoteMethodValueLottery
            vote = calculateVoteLotteryMethod();
        }

        // If the override is not all zeros (it is never null), check if it is in the current cycle. If the override
        // vote is in the current cycle, remove it (set it to all zeros). Otherwise, use it. If you really think out
        // this code, there is a race condition where we might check an old override, a new override could be set, and
        // then we might erase the new override. This is **extremely** unlikely, though, and not a serious issue if
        // it does happen (the override would not stick, and it would need to be sent again). So, we won't bother
        // addressing it.
        byte[] overrideIdentifier = NewVerifierVoteManager.getOverride();
        if (!ByteUtil.isAllZeros(overrideIdentifier)) {
            ByteBuffer overrideBuffer = ByteBuffer.wrap(overrideIdentifier);
            if (BlockManager.verifierInCurrentCycle(overrideBuffer)) {
                NewVerifierVoteManager.setOverride(new byte[FieldByteSize.identifier]);  // erase the override
            } else {
                vote = overrideBuffer;  // use the override
            }
        }

        return vote;
    }

    private static synchronized ByteBuffer calculateVoteFifoMethod() {

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
        return oldestNewVerifier == null ? null : ByteBuffer.wrap(oldestNewVerifier.getIdentifier());
    }

    private static synchronized ByteBuffer calculateVoteLotteryMethod() {

        Set<ByteBuffer> currentCycle = BlockManager.verifiersInCurrentCycleSet();

        // Get the reference block. This changes every 50 blocks.
        long referenceHashHeight = BlockManager.getFrozenEdgeHeight() / 50L * 50L;
        Block referenceBlock = BlockManager.frozenBlockForHeight(referenceHashHeight);

        // Only continue if the block is available. If it is not, which is highly unlikely, we cannot calculate a
        // reasonable vote.
        byte[] winningIdentifier = null;
        if (referenceBlock != null) {

            byte[] hash = referenceBlock.getHash();

            List<Node> mesh = NodeManager.getMesh();
            long thresholdTime = System.currentTimeMillis() - 1000L * 60L * 10L;
            int winningScore = Integer.MAX_VALUE;
            for (Node node : mesh) {

                // To avoid manipulation, only accept nodes that have been in the queue for 10 minutes or more. This
                // is about 85 blocks, and we are only looking back 50 blocks into the blockchain.
                ByteBuffer identifier = ByteBuffer.wrap(node.getIdentifier());
                if (!currentCycle.contains(identifier) && node.getQueueTimestamp() < thresholdTime) {
                    int score = score(hash, node.getIdentifier());
                    if (score < winningScore) {
                        winningScore = score;
                        winningIdentifier = node.getIdentifier();
                    }
                }
            }
        }

        return winningIdentifier == null ? null : ByteBuffer.wrap(winningIdentifier);
    }

    private static int score(byte[] hash, byte[] identifier) {

        // This method provides a simple, computationally cheap way to rank identifiers relative to a hash from the
        // blockchain. This allows us to have a lottery that is highly synchronized among all verifiers.

        // Some identifiers would be at a significant advantage in the naive distance calculation due to their digit
        // distributions, so we hash the bytewise sum of the identifier and block hash to eliminate this advantage.

        int score;
        if (hash.length != 32 || identifier.length != 32) {
            score = Integer.MAX_VALUE;
        } else {
            byte[] combinedArray = new byte[32];
            for (int i = 0; i < 32; i++) {
                combinedArray[i] = (byte) (hash[i] + identifier[i]);
            }
            byte[] hashedIdentifier = HashUtil.singleSHA256(combinedArray);

            score = 0;
            for (int i = 0; i < 32; i++) {
                int hashValue = hash[i] & 0xff;
                int identifierValue = hashedIdentifier[i] & 0xff;
                score += Math.abs(hashValue - identifierValue);
            }
        }

        return score;
    }

    public static ByteBuffer getCurrentVote() {
        return currentVote;
    }
}
