package co.nyzo.verifier;

import co.nyzo.verifier.messages.NewVerifierVote;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PreferencesUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class NewVerifierQueueManager {

    // The wait time for the lottery is 30 days.
    public static final long lotteryWaitTime = 1000L * 60L * 60L * 24L * 30L;

    private static long previousReferenceHashHeight = -1;
    private static ByteBuffer currentVote = null;

    private static final String previousReferenceHashHeightKey = "previous_reference_hash_height";
    private static final String winningIdentifierKey = "winning_identifier";

    public static synchronized void updateVote() {

        ByteBuffer vote = BlockManager.isInitialized() ? calculateVote() : null;

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
        }
    }

    private static synchronized ByteBuffer calculateVote() {

        // If the override is not all zeros (it is never null), check if it is in the current cycle. If the override
        // vote is in the current cycle, remove it (set it to all zeros). Otherwise, use it. If you really think out
        // this code, there is a race condition where we might check an old override, a new override could be set, and
        // then we might erase the new override. This is **extremely** unlikely, though, and not a serious issue if
        // it does happen (the override would not stick, and it would need to be sent again). So, we won't bother
        // addressing it.
        byte[] overrideIdentifier = NewVerifierVoteManager.getOverride();
        ByteBuffer vote = null;
        if (!ByteUtil.isAllZeros(overrideIdentifier)) {
            ByteBuffer overrideBuffer = ByteBuffer.wrap(overrideIdentifier);
            if (BlockManager.verifierInCurrentCycle(overrideBuffer)) {
                NewVerifierVoteManager.setOverride(new byte[FieldByteSize.identifier]);  // erase the override
            } else {
                vote = overrideBuffer;  // use the override
            }
        }

        // If the override was not used, calculate the vote by the lottery method.
        if (vote == null) {
            vote = calculateVoteLotteryMethod();
        }

        return vote;
    }

    private static synchronized ByteBuffer calculateVoteLotteryMethod() {

        Set<ByteBuffer> currentCycle = BlockManager.verifiersInCurrentCycleSet();

        // Get the reference height. This changes every 50 blocks.
        long referenceHashHeight = BlockManager.getFrozenEdgeHeight() / 50L * 50L;

        byte[] winningIdentifier = null;
        if (referenceHashHeight == previousReferenceHashHeight) {
            winningIdentifier = currentVote == null ? new byte[FieldByteSize.identifier] : currentVote.array();
        } else if (previousReferenceHashHeight == -1L &&
                referenceHashHeight == PersistentData.getLong(previousReferenceHashHeightKey, -1L) &&
                PersistentData.getByteArray(winningIdentifierKey, FieldByteSize.identifier, null) != null) {
            previousReferenceHashHeight = PersistentData.getLong(previousReferenceHashHeightKey, -1L);
            winningIdentifier = PersistentData.getByteArray(winningIdentifierKey, FieldByteSize.identifier, null);
        } else {
            Block referenceBlock = BlockManager.frozenBlockForHeight(referenceHashHeight);

            // Only continue if the block is available. If it is not, which is highly unlikely, we cannot calculate a
            // reasonable vote.
            if (referenceBlock != null) {

                // Get the "cycle hash" of the reference block. If this cannot be calculated properly, the returned
                // value will be all zeros.
                byte[] hash = calculateCycleHash(referenceBlock);
                if (!ByteUtil.isAllZeros(hash)) {

                    // Find the lowest-scored identifier that has been waiting sufficiently long.
                    long thresholdTime = System.currentTimeMillis() - lotteryWaitTime;
                    List<Node> mesh = NodeManager.getMesh();
                    int winningScore = Integer.MAX_VALUE;
                    for (Node node : mesh) {
                        ByteBuffer identifier = ByteBuffer.wrap(node.getIdentifier());
                        if (!currentCycle.contains(identifier) && node.getQueueTimestamp() < thresholdTime) {
                            int score = score(hash, node.getIdentifier());
                            if (score < winningScore) {
                                winningScore = score;
                                winningIdentifier = node.getIdentifier();
                            }
                        }
                    }

                    // If no verifier has been waiting long enough, select the verifier that has been waiting longest.
                    // This is a naive FIFO process, and it is only intended to be a convenient fallback to the lottery,
                    // not a robust standalone solution.
                    if (winningIdentifier == null) {
                        long winningTimestamp = System.currentTimeMillis();
                        for (Node node : mesh) {
                            ByteBuffer identifier = ByteBuffer.wrap(node.getIdentifier());
                            if (!currentCycle.contains(identifier) && node.getQueueTimestamp() < winningTimestamp) {
                                winningTimestamp = node.getQueueTimestamp();
                                winningIdentifier = node.getIdentifier();
                            }
                        }
                    }

                    // If a vote was successfully calculated, store the reference height so we can avoid recalculating
                    // the vote for this height.
                    if (winningIdentifier != null) {
                        previousReferenceHashHeight = referenceHashHeight;
                        PersistentData.put(previousReferenceHashHeightKey, previousReferenceHashHeight);
                        PersistentData.put(winningIdentifierKey, winningIdentifier);
                    }
                }
            }
        }

        return winningIdentifier == null ? null : ByteBuffer.wrap(winningIdentifier);
    }

    private static byte[] calculateCycleHash(Block referenceBlock) {

        // To ensure a verifiably fair lottery, this method uses blockchain data to determine a reference for the
        // distance calculation. To avoid a single verifier being able to choose the reference, one bit is contributed
        // to the "cycle-hash" input from each block hash in the cycle. This is not immune to manipulation, but it does
        // significantly reduce each individual verifier's potential for manipulation.

        byte[] cycleHash = new byte[FieldByteSize.hash];
        if (referenceBlock != null && referenceBlock.getCycleInformation() != null) {

            // This finds the smallest number of bytes that will allow one bit from each block to be used.
            boolean calculationIsValid = true;
            int cycleLength = referenceBlock.getCycleInformation().getCycleLength();
            int numberOfBytes = (cycleLength + 7) / 8;
            byte[] bitString = new byte[numberOfBytes];
            for (int j = 0; j < numberOfBytes && calculationIsValid; j++) {
                int value = 0;
                for (int i = 0; i < 8 && calculationIsValid; i++) {
                    long blockHeight = referenceBlock.getBlockHeight() - cycleLength + i + j * 8 + 1;
                    value *= 2;
                    if (blockHeight <= referenceBlock.getBlockHeight()) {
                        Block block = BlockManager.frozenBlockForHeight(blockHeight);
                        if (block == null) {
                            calculationIsValid = false;
                        } else {
                            // This is the contribution of the block to the cycle hash. In Java, byte values range from
                            // -128 to 127. So, the range of values less than zero is exactly the same size as the range
                            // of values greater than or equal to zero. In other words, all we use is the sign bit of
                            // the first byte of each hash.
                            byte blockHashValue = block.getHash()[0];
                            value += blockHashValue < 0 ? 1 : 0;
                        }
                    }
                }
                bitString[j] = (byte) value;
            }

            // The final output is the hash of the bit string produced by the cycle hash values.
            if (calculationIsValid) {
                cycleHash = HashUtil.singleSHA256(bitString);
            }
        }

        return cycleHash;
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
