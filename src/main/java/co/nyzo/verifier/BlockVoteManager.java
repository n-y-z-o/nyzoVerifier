package co.nyzo.verifier;

import co.nyzo.verifier.messages.BlockVote;
import co.nyzo.verifier.messages.MissingBlockVoteRequest;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.NotificationUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class BlockVoteManager {

    public static final long minimumVoteInterval = 5000L;

    private static final Map<Long, Map<ByteBuffer, BlockVote>> voteMap = new HashMap<>();
    private static final Map<Long, Map<ByteBuffer, BlockVote>> flipVoteMap = new HashMap<>();

    private static int numberOfVotesRequested = 0;
    private static long lastVoteRequestTimestamp = 0L;

    public static synchronized void registerVote(Message message) {

        BlockVote vote = (BlockVote) message.getContent();
        if (vote != null) {
            
            // Set the receipt timestamp and store message information on the vote.
            vote.setReceiptTimestamp(System.currentTimeMillis());
            vote.setSenderIdentifier(message.getSourceNodeIdentifier());
            vote.setMessageTimestamp(message.getTimestamp());
            vote.setMessageSignature(message.getSourceNodeSignature());

            // Register the vote. The map ensures that each identifier only gets one vote. Votes are only counted for
            // verifiers in the current cycle, except in the Genesis cycle, where all votes are counted. We accept votes
            // all the way to the open edge, in case we have gotten behind and need to catch up.
            long height = vote.getHeight();
            long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
            ByteBuffer identifierBuffer = ByteBuffer.wrap(message.getSourceNodeIdentifier());
            if (height > frozenEdgeHeight &&
                    height <= BlockManager.openEdgeHeight(true) &&
                    !ByteUtil.isAllZeros(vote.getHash()) &&
                    (BlockManager.verifierInCurrentCycle(identifierBuffer) || BlockManager.inGenesisCycle())) {

                // Get the map for the height.
                Map<ByteBuffer, BlockVote> votesForHeight = voteMap.computeIfAbsent(height, k -> new HashMap<>());

                BlockVote existingVote = votesForHeight.get(identifierBuffer);
                if (existingVote == null) {

                    // If the existing vote is null, we always accept the new vote.
                    votesForHeight.put(identifierBuffer, vote);

                } else if (!ByteUtil.arraysAreEqual(existingVote.getHash(), vote.getHash())) {

                    // If the new vote is different, we require two new votes for the same hash, more than 5 seconds
                    // apart, to flip the vote.

                    // Get the flip map for the height.
                    Map<ByteBuffer, BlockVote> flipVotesForHeight = flipVoteMap.computeIfAbsent(height, k -> new HashMap<>());

                    BlockVote existingFlipVote = flipVotesForHeight.get(identifierBuffer);
                    if (existingFlipVote == null ||
                            !ByteUtil.arraysAreEqual(existingFlipVote.getHash(), vote.getHash())) {

                        // If the existing flip vote is null or different than the new vote, we store the new vote in
                        // the flip map to wait for another vote.
                        flipVotesForHeight.put(identifierBuffer, vote);

                    } else if (vote.getTimestamp() - existingFlipVote.getTimestamp() > minimumVoteInterval &&
                            vote.getReceiptTimestamp() - existingFlipVote.getReceiptTimestamp() > minimumVoteInterval) {

                        // The new vote matches the flip vote, and the minimum intervals have been met. Flip the vote
                        // in the primary map. There is no need to clear the flip vote entry; leaving the entry there
                        // does not affect subsequent operations.
                        votesForHeight.put(identifierBuffer, vote);
                    }
                }
            }
        }
    }

    public static synchronized void removeOldVotes() {

        // This method used to remove all votes before the frozen edge. Now, to support off-cycle verifiers, votes are
        // retained for 40 blocks behind the frozen edge.
        Set<Long> heights = new HashSet<>(voteMap.keySet());
        long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
        for (long height : heights) {
            if (height <= frozenEdgeHeight - 40) {
                try {
                    System.out.println("$$$$$ removing vote map of size " + voteMap.get(height).size() + "");
                } catch (Exception ignored) { }
                voteMap.remove(height);
                flipVoteMap.remove(height);
            }
        }
    }

    public static synchronized String votesAtHeight(long height) {

        int numberOfVotes = 0;
        int maximumVotes = 0;
        Map<ByteBuffer, BlockVote> votesForHeight = voteMap.get(height);
        if (votesForHeight != null) {
            numberOfVotes = votesForHeight.size();

            Map<ByteBuffer, Integer> voteCounts = new HashMap<>();
            for (BlockVote vote : votesForHeight.values()) {
                ByteBuffer hash = ByteBuffer.wrap(vote.getHash());
                Integer count = voteCounts.get(hash);
                if (count == null) {
                    count = 1;
                } else {
                    count++;
                }
                voteCounts.put(hash, count);
                maximumVotes = Math.max(maximumVotes, count);
            }
        }

        return numberOfVotes + "(" + maximumVotes + ")";
    }

    public static synchronized Map<ByteBuffer, BlockVote> votesForHeight(long height) {

        Map<ByteBuffer, BlockVote> votesForHeight = voteMap.get(height);
        return votesForHeight == null ? null : new HashMap<>(votesForHeight);
    }

    public static int numberOfVotesAtHeight(long height) {

        Map<ByteBuffer, BlockVote> votesForHeight = voteMap.get(height);
        return votesForHeight == null ? 0 : votesForHeight.size();
    }

    public static synchronized List<Long> getHeights() {

        List<Long> heights = new ArrayList<>(voteMap.keySet());
        Collections.sort(heights);

        return heights;
    }

    public static synchronized Set<ByteBuffer> getHashesForHeight(long height) {

        Set<ByteBuffer> hashes = new HashSet<>();
        Map<ByteBuffer, BlockVote> votesForHeight = voteMap.get(height);
        if (votesForHeight != null) {
            for (BlockVote vote : votesForHeight.values()) {
                hashes.add(ByteBuffer.wrap(vote.getHash()));
            }
        }
        hashes.remove(ByteBuffer.wrap(new byte[FieldByteSize.hash]));  // remove the empty hash, if present

        return hashes;
    }

    public static synchronized byte[] leadingHashForHeight(long height, AtomicInteger leadingHashVoteCount) {

        byte[] leadingHash = null;
        Map<ByteBuffer, BlockVote> votesForHeight = voteMap.get(height);
        if (votesForHeight != null) {

            Map<ByteBuffer, Integer> votesPerHash = new HashMap<>();

            // Build the vote map.
            for (ByteBuffer identifier : votesForHeight.keySet()) {
                ByteBuffer hash = ByteBuffer.wrap(votesForHeight.get(identifier).getHash());
                votesPerHash.merge(hash, 1, (a, b) -> a + b);
            }

            // Get the hash with the most votes.
            for (ByteBuffer hash : votesPerHash.keySet()) {
                int hashVoteCount = votesPerHash.get(hash);
                if (hashVoteCount > leadingHashVoteCount.get()) {
                    leadingHashVoteCount.set(hashVoteCount);
                    leadingHash = hash.array();
                }
            }
        }

        return leadingHash;
    }

    public static synchronized byte[] getLocalVoteForHeight(long height) {

        byte[] hash = null;
        if (voteMap.containsKey(height)) {
            BlockVote vote = voteMap.get(height).get(ByteBuffer.wrap(Verifier.getIdentifier()));
            if (vote != null) {
                hash = vote.getHash();
            }
        }

        return hash;
    }

    public static int getNumberOfVotesRequested() {

        return numberOfVotesRequested;
    }

    public static synchronized void requestMissingVotes() {

        // Only request missing votes outside the Genesis cycle, and only request if we have more than 50% of votes
        // at a height more than one past the frozen edge. This indicates that this verifier is receiving messages
        // from the mesh again but had an outage that caused it to miss earlier votes.

        // Also, to conserve local CPU and mesh bandwidth, limit vote requests to no more frequently than once every
        // two seconds.
        long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
        long currentTimestamp = System.currentTimeMillis();
        if (!BlockManager.inGenesisCycle() && currentTimestamp - lastVoteRequestTimestamp > 2000L) {

            // Look through all heights in the vote map. If the vote is greater than 50% and the height is greater than
            // one more than the frozen edge, a vote request should be performed.
            boolean shouldRequest = false;
            for (Long height : voteMap.keySet()) {
                if (!shouldRequest && height > frozenEdgeHeight + 1 &&  // check of !shouldRequest for performance only
                        voteMap.get(height).size() > BlockManager.currentCycleLength() / 2) {
                    shouldRequest = true;
                }
            }

            // As a fallback, also request if the last block was frozen 30 seconds ago or more and the last
            // vote request was 15 seconds ago or more.
            shouldRequest |= currentTimestamp - Verifier.getLastBlockFrozenTimestamp() > 30000L &&
                    currentTimestamp - lastVoteRequestTimestamp > 15000L;

            if (shouldRequest) {
                // We will request votes from all verifiers in the current cycle, even those we already have. Some
                // votes may have changed.
                Set<ByteBuffer> verifiersInCurrentCycle = BlockManager.verifiersInCurrentCycleSet();

                // Set the last-vote-request timestamp now. We will also set it in the response to ensure a minimum gap.
                lastVoteRequestTimestamp = System.currentTimeMillis();

                // We only work to freeze one past the frozen edge.
                long heightToRequest = frozenEdgeHeight + 1;

                NotificationUtil.send("Need to request " + verifiersInCurrentCycle.size() + " votes for height " +
                        heightToRequest + " on " + Verifier.getNickname(), frozenEdgeHeight);

                // Finally, request the votes.
                Message message = new Message(MessageType.MissingBlockVoteRequest23,
                        new MissingBlockVoteRequest(heightToRequest));
                for (Node node : NodeManager.getMesh()) {

                    if (verifiersInCurrentCycle.contains(ByteBuffer.wrap(node.getIdentifier()))) {

                        numberOfVotesRequested++;

                        Message.fetch(IpUtil.addressAsString(node.getIpAddress()), node.getPort(), message,
                                message1 -> {

                                    BlockVote vote = (BlockVote) message1.getContent();
                                    if (vote != null) {
                                        registerVote(message1);

                                        // Each time a good vote is received, the last-vote-request timestamp is
                                        // updated. If we take some time to request all the votes, this helps to
                                        // avoid starting a new round of requests soon after, or even before, this
                                        // round of requests completes.
                                        lastVoteRequestTimestamp = System.currentTimeMillis();
                                    }
                                });
                    }
                }
            }
        }
    }
}
