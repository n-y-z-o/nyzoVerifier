package co.nyzo.verifier;

import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FrozenBlockVoteTally {

    private long blockHeight;  // helpful for debugging; not necessary otherwise
    private Set<ByteBuffer> identifiersThatHaveVoted;
    private Map<ByteBuffer, Integer> hashVoteMap;

    public FrozenBlockVoteTally(long blockHeight) {
        this.blockHeight = blockHeight;
        this.identifiersThatHaveVoted = new HashSet<>();
        this.hashVoteMap = new HashMap<>();
    }

    public boolean vote(byte[] identifier, byte[] hash) {

        ByteBuffer identifierBuffer = ByteBuffer.wrap(identifier);
        ByteBuffer hashBuffer = ByteBuffer.wrap(hash);
        Integer count = hashVoteMap.get(hashBuffer);
        if (count == null) {
            count = 0;
        }

        if (!identifiersThatHaveVoted.contains(identifierBuffer)) {
            identifiersThatHaveVoted.add(identifierBuffer);
            count++;
            hashVoteMap.put(hashBuffer, count);
            System.out.println("count is now " + count + " for hash " + PrintUtil.compactPrintByteArray(hash) +
                    " at height " + blockHeight);
        }

        return count >= NodeManager.numberOfNodesInMesh() / 2;
    }

    public int totalVotes() {

        int totalVotes = 0;
        for (Integer votes : hashVoteMap.values()) {
            totalVotes += votes;
        }

        System.out.println("have " + totalVotes + " total votes at height " + blockHeight);
        return totalVotes;
    }

    public int votesForWinner(byte[] winnerHash) {

        int votesForWinner = -1;
        for (ByteBuffer hashBuffer : hashVoteMap.keySet()) {
            int votes = hashVoteMap.get(hashBuffer);
            if (votes > votesForWinner) {
                votesForWinner = votes;
                System.arraycopy(hashBuffer.array(), 0, winnerHash, 0, FieldByteSize.hash);
            }
        }

        System.out.println("have " + votesForWinner + " votes for winner " +
                PrintUtil.compactPrintByteArray(winnerHash) + " at height " + blockHeight);
        return votesForWinner;
    }
}
