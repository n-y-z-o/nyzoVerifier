package co.nyzo.verifier;

import co.nyzo.verifier.messages.NewVerifierVote;

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

    public static List<ByteBuffer> topVerifiers() {

        // Make and sort the list descending on votes.
        Map<ByteBuffer, Integer> voteTotals = voteTotals();
        List<ByteBuffer> topVerifiers = new ArrayList<>(voteTotals.keySet());
        Collections.sort(topVerifiers, new Comparator<ByteBuffer>() {
            @Override
            public int compare(ByteBuffer verifierVote1, ByteBuffer verifierVote2) {
                Integer voteCount1 = voteTotals.get(verifierVote1);
                Integer voteCount2 = voteTotals.get(verifierVote2);
                return voteCount2.compareTo(voteCount1);
            }
        });

        // Limit the list to three verifiers. We do not consider ties, as they are inconsequential and do not
        // justify additional logic complexity.
        while (topVerifiers.size() > 3) {
            topVerifiers.remove(topVerifiers.size() - 1);
        }

        // If the verifiers list is empty and this is a new verifier, add it to the list now.
        if (topVerifiers.isEmpty()) {
            ByteBuffer verifierIdentifier = ByteBuffer.wrap(Verifier.getIdentifier());
            if (!BlockManager.verifierInCurrentCycle(verifierIdentifier)) {
                topVerifiers.add(verifierIdentifier);
            }
        }

        return topVerifiers;
    }

    public static NewVerifierVote getLocalVote() {

        return localVote;
    }
}
