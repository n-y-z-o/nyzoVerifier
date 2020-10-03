package co.nyzo.verifier;

import co.nyzo.verifier.messages.NewVerifierVote;
import co.nyzo.verifier.util.*;

import java.nio.ByteBuffer;
import java.util.*;

public class NewVerifierQueueManager {

    private static final long maximumIpValueInterval = 16L;

    // The wait time for the lottery is 30 days.
    public static final long lotteryWaitTime = 1000L * 60L * 60L * 24L * 30L;

    // To help break stalls due to missing top-new-verifier votes, the votes are rebroadcast every 30 blocks in time,
    // even if they do not change.
    private static long lastBroadcastTimestamp = 0L;
    private static final long broadcastInterval = 30 * Block.blockDuration;

    private static long previousReferenceHashHeight = -1;
    private static ByteBuffer currentVote = null;

    private static final String previousReferenceHashHeightKey = "previous_reference_hash_height";
    private static final String winningIdentifierKey = "winning_identifier";

    public static void updateVote() {

        ByteBuffer vote = BlockManager.completedInitialization() ? calculateVote() : null;
        // If the vote has changed, or if the amount of time since the last vote has been greater than the interval,
        // register and broadcast.
        if (vote != null && (!vote.equals(currentVote) ||
                lastBroadcastTimestamp < System.currentTimeMillis() - broadcastInterval)) {

            lastBroadcastTimestamp = System.currentTimeMillis();
            LogUtil.println("broadcasting new-verifier vote for " + PrintUtil.compactPrintByteArray(vote.array()) +
                    " at height " + BlockManager.getFrozenEdgeHeight());

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
                // do not have voting power. However, informing the out-of-cycle subjects of these vote changes is
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
                byte[] cycleHash = calculateCycleHash(referenceBlock);
                if (!ByteUtil.isAllZeros(cycleHash)) {

                    // Get the value for the hash. This is used to select the node.
                    Node winningNode = winningNodeForCycleHash(currentCycle, cycleHash, referenceHashHeight);
                    winningIdentifier = winningNode.getIdentifier();

                    // If no verifier has been waiting long enough, select the verifier that has been waiting longest.
                    // This is a naive FIFO process, and it is only intended to be a convenient fallback to the lottery,
                    // not a robust standalone solution.
                    if (winningIdentifier == null) {
                        long winningTimestamp = System.currentTimeMillis();
                        List<Node> mesh = NodeManager.getMesh();
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

    private static Node winningNodeForCycleHash(Set<ByteBuffer> currentCycle, byte[] cycleHash, long cycleHashHeight) {

        // Build the value-to-node map with all nodes that are not in the cycle and have been waiting long enough.
        Map<Long, Node> valueToNodeMap = new HashMap<>();
        long thresholdTime = System.currentTimeMillis() - lotteryWaitTime;
        List<Node> mesh = NodeManager.getMesh();
        for (Node node : mesh) {
            ByteBuffer identifier = ByteBuffer.wrap(node.getIdentifier());
            if (!currentCycle.contains(identifier) && node.getQueueTimestamp() < thresholdTime) {
                long nodeValue = ipToLong(node.getIpAddress());
                valueToNodeMap.put(nodeValue, node);
            }
        }

        Node winningNode = null;
        if (valueToNodeMap.size() == 1) {
            // This is an exceptional situation, but it is worth handling here to allow an assumption of a map size of
            // at least 2 in the next condition.
            winningNode = valueToNodeMap.values().iterator().next();
        } else if (valueToNodeMap.size() > 1) {
            // Iterate through the values. Each IP gets a number of chances based on its proximity to the previous and
            // next addresses in the list. These chances are scattered via hashing, mapped to the same [0 - 1] range as
            // the cycle hash, and the closest distance is the winner.
            List<Long> values = new ArrayList<>(valueToNodeMap.keySet());
            Collections.sort(values);
            byte[] winningHash = null;
            for (int i = 0; i < values.size(); i++) {
                long value = values.get(i);
                long previousInterval = Math.min(maximumIpValueInterval, i == 0 ? values.get(1) - value : value -
                        values.get(i - 1));
                long nextInterval = Math.min(maximumIpValueInterval, i == values.size() - 1 ? previousInterval :
                        values.get(i + 1) - value);

                long startIndex = value - (previousInterval - 1) / 2;
                long endIndex = value + (nextInterval - 1) / 2;
                for (long j = startIndex; j <= endIndex; j++) {
                    long ipHashInput = j + cycleHashHeight;  // add the cycle hash height to introduce variability
                    byte[] ipHash = HashUtil.singleSHA256(HashUtil.byteArray(ipHashInput));
                    winningHash = closerHash(cycleHash, winningHash, ipHash);
                    if (ByteUtil.arraysAreEqual(ipHash, winningHash)) {
                        winningNode = valueToNodeMap.get(value);
                    }
                }
            }

        }

        return winningNode;
    }

    private static long ipToLong(byte[] address) {
        return (address[0] & 0xffL) << 24 |
                (address[1] & 0xffL) << 16 |
                (address[2] & 0xffL) << 8 |
                (address[3] & 0xffL);
    }

    private static byte[] closerHash(byte[] referenceHash, byte[] hash0, byte[] hash1) {
        byte[] closerHash = null;
        if (hash0 == null || hash0.length != FieldByteSize.hash) {
            closerHash = hash1;
        } else if (hash1 == null || hash1.length != FieldByteSize.hash) {
            closerHash = hash0;
        } else {
            for (int i = 0; i < FieldByteSize.hash && closerHash == null; i++) {
                int distance0 = Math.abs((referenceHash[i] & 0xff) - (hash0[i] & 0xff));
                int distance1 = Math.abs((referenceHash[i] & 0xff) - (hash1[i] & 0xff));
                if (distance0 < distance1) {
                    closerHash = hash0;
                } else if (distance1 < distance0) {
                    closerHash = hash1;
                }
            }

            if (closerHash == null) {
                closerHash = hash0;
            }
        }

        return closerHash;
    }

    public static ByteBuffer getCurrentVote() {
        return currentVote;
    }
}
