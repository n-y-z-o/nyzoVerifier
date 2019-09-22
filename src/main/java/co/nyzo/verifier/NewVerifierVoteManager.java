package co.nyzo.verifier;

import co.nyzo.verifier.messages.NewVerifierVote;
import co.nyzo.verifier.util.LogUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NewVerifierVoteManager {

    // This class manages votes for the next verifier to let into the verification cycle. Each existing verifier has a
    // vote for who should be allowed in next, and the winning verifier is the one who should be admitted. This is a
    // vote that can (and should) be recast many times, as many new verifiers are let into the mesh.

    // As much as possible, this class follows the pattern of BlockVoteManager.

    // The local vote is redundant, but it is a simple and efficient way to store the local vote for responding to
    // node-join messages.
    private static NewVerifierVote localVote = new NewVerifierVote(new byte[FieldByteSize.identifier]);
    private static final Map<ByteBuffer, ByteBuffer> voteMap = new ConcurrentHashMap<>();
    private static ByteBuffer topVerifier = null;

    private static byte[] override = new byte[FieldByteSize.identifier];

    public static void setOverride(byte[] override) {
        NewVerifierVoteManager.override = override;
    }

    public static byte[] getOverride() {
        return override;
    }

    public static void registerVote(byte[] votingIdentifier, NewVerifierVote vote, boolean isLocalVote) {

        // Register the vote. The map ensures that each identifier only gets one vote. Some of the votes may not count.
        // Votes are only counted for verifiers in the previous cycle.
        ByteBuffer votingIdentifierBuffer = ByteBuffer.wrap(votingIdentifier);
        if (BlockManager.verifierInCurrentCycle(votingIdentifierBuffer)) {
            voteMap.put(votingIdentifierBuffer, ByteBuffer.wrap(vote.getIdentifier()));
        }

        if (isLocalVote) {
            localVote = vote;
        }
    }

    public static void removeOldVotes() {

        // For simplicity, we remove all votes that are not in the current verifier cycle to conserve memory. This will
        // potentially remove verifiers that would be in the verification cycle when they are needed, but that's not
        // a huge concern, as this process does not require absolute or precise vote counts to work properly. In
        // reality, we are highly unlikely to ever lose more than one vote at a time due to this simplification.

        Set<ByteBuffer> verifiers = new HashSet<>(voteMap.keySet());
        for (ByteBuffer verifier : verifiers) {
            if (!BlockManager.verifierInCurrentCycle(verifier)) {
                voteMap.remove(verifier);
            }
        }
    }

    public static Map<ByteBuffer, Integer> voteTotals() {

        Map<ByteBuffer, Integer> voteTotals = new HashMap<>();
        for (ByteBuffer votingVerifier : voteMap.keySet()) {
            if (BlockManager.verifierInCurrentCycle(votingVerifier)) {
                ByteBuffer vote = voteMap.get(votingVerifier);
                if (vote != null && !BlockManager.verifierInCurrentCycle(vote)) {
                    Integer votesForVerifier = voteTotals.getOrDefault(vote, 0);
                    voteTotals.put(vote, votesForVerifier + 1);
                }
            }
        }

        return voteTotals;
    }

    public static ByteBuffer topVerifier() {
        return topVerifier;
    }

    public static void updateTopVerifier() {

        // Find the verifier with the most votes. The top vote count is initially at least half of the cycle length,
        // which means that the top verifier will remain null if no verifier has the majority of votes.
        ByteBuffer topVerifier = null;
        int topVoteCount = (BlockManager.currentCycleLength() + 1) / 2;
        Map<ByteBuffer, Integer> voteTotals = voteTotals();
        for (ByteBuffer verifier : voteTotals.keySet()) {
            int voteCount = voteTotals.get(verifier);
            if (voteCount > topVoteCount) {
                topVoteCount = voteCount;
                topVerifier = verifier;
            }
        }
        if (topVerifier == null) {
            LogUtil.println("top verifier is null");
        } else {
            int cycleLength = BlockManager.currentCycleLength();
            LogUtil.println(String.format("top verifier %s has %d votes with a cycle length of %d (%.1f%%)",
                    PrintUtil.compactPrintByteArray(topVerifier.array()), topVoteCount, cycleLength,
                    topVoteCount * 100.0 / cycleLength));
        }

        NewVerifierVoteManager.topVerifier = topVerifier;
    }

    public static NewVerifierVote getLocalVote() {

        return localVote;
    }
}
