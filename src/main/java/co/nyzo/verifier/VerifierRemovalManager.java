package co.nyzo.verifier;

import co.nyzo.verifier.messages.VerifierRemovalVote;
import co.nyzo.verifier.util.IpUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VerifierRemovalManager {

    private static final Map<ByteBuffer, List<byte[]>> voteMap = new ConcurrentHashMap<>();
    private static Map<ByteBuffer, Integer> voteCounts = new ConcurrentHashMap<>();

    public static void registerVote(byte[] votingIdentifier, VerifierRemovalVote vote) {

        // Register the vote. The map ensures that each identifier only gets one vote. Votes are only counted for
        // verifiers in the cycle.
        ByteBuffer votingIdentifierBuffer = ByteBuffer.wrap(votingIdentifier);
        if (BlockManager.verifierInCurrentCycle(votingIdentifierBuffer)) {
            voteMap.put(votingIdentifierBuffer, vote.getIdentifiers());
        }
    }

    public static void removeOldVotes() {

        // For simplicity, we remove all votes that are not in the current verifier cycle to conserve memory. This will
        // potentially remove verifiers that would be in the verification cycle when they are needed, but that's not
        // a huge concern, as this process does not require absolute or precise vote counts to work properly. In
        // reality, we are highly unlikely to ever lose more than one verifier's vote at a time due to this
        // simplification.

        Set<ByteBuffer> verifiers = new HashSet<>(voteMap.keySet());
        Set<ByteBuffer> currentCycle = BlockManager.verifiersInCurrentCycleSet();
        for (ByteBuffer verifier : verifiers) {
            if (!currentCycle.contains(verifier)) {
                voteMap.remove(verifier);
            }
        }
    }

    public static void updateVoteCounts() {

        Map<ByteBuffer, Integer> voteCounts = new ConcurrentHashMap<>();
        for (ByteBuffer votingVerifier : voteMap.keySet()) {
            if (BlockManager.verifierInCurrentCycle(votingVerifier)) {
                List<byte[]> targetVerifiers = voteMap.get(votingVerifier);
                if (targetVerifiers != null) {
                    for (byte[] targetVerifier : targetVerifiers) {
                        ByteBuffer targetVerifierBuffer = ByteBuffer.wrap(targetVerifier);
                        if (BlockManager.verifierInCurrentCycle(targetVerifierBuffer)) {
                            Integer votesForTargetVerifier = voteCounts.getOrDefault(targetVerifierBuffer, 0);
                            voteCounts.put(targetVerifierBuffer, votesForTargetVerifier + 1);
                        }
                    }
                }
            }
        }

        VerifierRemovalManager.voteCounts = voteCounts;
    }

    public static Map<ByteBuffer, Integer> getVoteCounts() {

        return voteCounts;
    }

    public static boolean shouldPenalizeVerifier(byte[] identifier) {

        int cycleLength = BlockManager.currentCycleLength();
        return BlockManager.getLastVerifierRemovalHeight() < BlockManager.getFrozenEdgeHeight() - cycleLength &&
                voteCounts.getOrDefault(ByteBuffer.wrap(identifier), 0) > cycleLength / 2;
    }
}
