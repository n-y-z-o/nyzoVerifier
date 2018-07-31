package co.nyzo.verifier;

import co.nyzo.verifier.messages.BlockVote;
import co.nyzo.verifier.messages.MissingBlockVoteRequest;
import co.nyzo.verifier.messages.StatusResponse;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.NotificationUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class BlockVoteManager {

    private static final List<String> recentVotes = new ArrayList<>();

    private static final ByteBuffer invalidVote = ByteBuffer.wrap(new byte[FieldByteSize.hash]);

    // The local votes map is redundant, but it is a simple and efficient way to store local votes for responding to
    // node-join messages.
    private static final Map<Long, BlockVote> localVotes = new HashMap<>();
    private static final Map<Long, Map<ByteBuffer, ByteBuffer>> voteMap = new HashMap<>();

    public static synchronized void registerVote(byte[] identifier, BlockVote vote, boolean isLocalVote) {

        // Register the vote. The map ensures that each identifier only gets one vote. Some of the votes may not count.
        // Votes are only counted for verifiers in the current cycle.
        long height = vote.getHeight();
        if (height > BlockManager.getFrozenEdgeHeight() && height < BlockManager.openEdgeHeight(true)) {
            Map<ByteBuffer, ByteBuffer> votesForHeight = voteMap.get(height);
            if (votesForHeight == null) {
                votesForHeight = new HashMap<>();
                voteMap.put(height, votesForHeight);
            }

            // If the identifier has already voted for a different hash, we cancel the votes. Otherwise, we store the
            // vote.
            ByteBuffer identifierBuffer = ByteBuffer.wrap(identifier);
            if (votesForHeight.containsKey(identifierBuffer) &&
                    !ByteUtil.arraysAreEqual(votesForHeight.get(identifierBuffer).array(), vote.getHash())) {
                votesForHeight.put(identifierBuffer, invalidVote);
                NotificationUtil.send("canceling vote for " + NicknameManager.get(identifier) + " on " +
                        Verifier.getNickname());
            } else {
                votesForHeight.put(identifierBuffer, ByteBuffer.wrap(vote.getHash()));
            }
        }

        if (isLocalVote) {
            localVotes.put(vote.getHeight(), vote);

            recentVotes.add("@" + vote.getHeight() + " for " + NicknameManager.get(vote.getHash()));
            while (recentVotes.size() > 10) {
                recentVotes.remove(0);
            }
        }
    }

    public static synchronized void removeOldVotes() {

        Set<Long> heights = new HashSet<>(voteMap.keySet());
        long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
        for (long height : heights) {
            if (height <= frozenEdgeHeight) {
                voteMap.remove(height);
                localVotes.remove(height);
            }
        }
    }

    // TODO: this method is for testing and will likely be removed before release
    public static synchronized String votesAtHeight(long height) {

        int numberOfVotes = 0;
        int maximumVotes = 0;
        Map<ByteBuffer, ByteBuffer> votesForHeight = voteMap.get(height);
        if (votesForHeight != null) {
            numberOfVotes = votesForHeight.size();

            Map<ByteBuffer, Integer> voteCounts = new HashMap<>();
            for (ByteBuffer byteBuffer : votesForHeight.values()) {
                Integer count = voteCounts.get(byteBuffer);
                if (count == null) {
                    count = 1;
                } else {
                    count++;
                }
                voteCounts.put(byteBuffer, count);
                maximumVotes = Math.max(maximumVotes, count);
            }
        }

        return numberOfVotes + "(" + maximumVotes + ")";
    }

    public static synchronized List<Long> getHeights() {

        List<Long> heights = new ArrayList<>(voteMap.keySet());
        Collections.sort(heights);

        return heights;
    }

    private static Set<ByteBuffer> votingVerifiers() {

        // For most blocks, the voting verifiers are the verifiers in the cycle of the frozen edge.
        Set<ByteBuffer> votingVerifiers = BlockManager.verifiersInCurrentCycle();

        // This is a work-around for the Genesis cycle only. This is not especially robust, but it does not matter,
        // because it will only be used for the first cycle at the beginning of the block chain.
        if (BlockManager.inGenesisCycle()) {
            votingVerifiers.clear();
            for (Node node : NodeManager.getMesh()) {
                votingVerifiers.add(ByteBuffer.wrap(node.getIdentifier()));
            }
        }

        return votingVerifiers;
    }

    public static synchronized byte[] winningHashForHeight(long height) {

        byte[] winningHash = null;
        Map<ByteBuffer, ByteBuffer> votesForHeight = voteMap.get(height);
        if (votesForHeight != null) {

            Map<ByteBuffer, Integer> votesPerHash = new HashMap<>();
            Set<ByteBuffer> votingVerifiers = votingVerifiers();

            // Build the vote map.
            for (ByteBuffer identifier : votesForHeight.keySet()) {
                if (votingVerifiers.contains(identifier)) {
                    ByteBuffer hash = votesForHeight.get(identifier);
                    Integer votesForHash = votesPerHash.get(hash);
                    if (votesForHash == null) {
                        votesPerHash.put(hash, 1);
                    } else {
                        votesPerHash.put(hash, votesForHash + 1);
                    }
                }
            }

            // Check the vote totals to see if any block should be frozen.
            long threshold = votingVerifiers.size() * 3L / 4L;
            int maximumVotes = 0;
            for (ByteBuffer hash : votesPerHash.keySet()) {
                maximumVotes = Math.max(maximumVotes, votesPerHash.get(hash));
                if (votesPerHash.get(hash) > threshold) {
                    winningHash = hash.array();
                }
            }

            if (height == BlockManager.getFrozenEdgeHeight() + 1L) {
                StatusResponse.setField("vote", "m: " + maximumVotes + ", t: " + threshold + ", " +
                        PrintUtil.compactPrintByteArray(winningHash) + ", h: +" +
                        (height - BlockManager.getFrozenEdgeHeight()));
            }
        }

        return winningHash;
    }

    public static synchronized List<String> getRecentVotes() {

        return new ArrayList<>(recentVotes);
    }

    public static synchronized List<BlockVote> getLocalVotes() {

        return new ArrayList<>(localVotes.values());
    }

    public static synchronized BlockVote getLocalVoteForHeight(long height) {

        return localVotes.get(height);
    }

    public static synchronized void requestMissingVotes() {

        // Start with a null map to avoid extra work if no heights are low enough to require use.
        Map<ByteBuffer, Node> votingVerifiers = null;

        // For any block more than 4 from the open edge, request any votes that appear to be missing. We are not using
        // the leading edge here, because it could be influenced by missing blocks, also, and we are likely in a bad
        // state if we need to request votes from the mesh.
        long openEdgeHeight = BlockManager.openEdgeHeight(false);
        for (Long height : voteMap.keySet()) {
            if (height < openEdgeHeight - 4) {

                // Build the map if not yet built.
                if (votingVerifiers == null) {
                    votingVerifiers = new HashMap<>();
                    Set<ByteBuffer> verifierIdentifiers = votingVerifiers();
                    for (Node node : NodeManager.getMesh()) {
                        ByteBuffer identifier = ByteBuffer.wrap(node.getIdentifier());
                        if (verifierIdentifiers.contains(identifier)) {
                            votingVerifiers.put(identifier, node);
                        }
                    }
                }

                // Get the list of verifiers at this height for which votes are already registered.
                Set<ByteBuffer> currentVotes = voteMap.get(height).keySet();

                // Send a message to every verifier for which we have not registered a vote for this height.
                Message message = new Message(MessageType.MissingBlockVoteRequest23,
                        new MissingBlockVoteRequest(height));
                for (ByteBuffer identifier : votingVerifiers.keySet()) {
                    if (!currentVotes.contains(identifier)) {
                        //NotificationUtil.send("sending request for vote for height " + height + " to " +
                        //        NicknameManager.get(identifier.array()) + " from " + Verifier.getNickname());

                        Node node = votingVerifiers.get(identifier);
                        Message.fetch(IpUtil.addressAsString(node.getIpAddress()), node.getPort(), message,
                                new MessageCallback() {
                                    @Override
                                    public void responseReceived(Message message) {

                                        BlockVote vote = (BlockVote) message.getContent();
                                        if (vote != null) {
                                            registerVote(message.getSourceNodeIdentifier(), vote, false);
                                        }
                                    }
                                });
                    }
                }
            }
        }

    }
}
