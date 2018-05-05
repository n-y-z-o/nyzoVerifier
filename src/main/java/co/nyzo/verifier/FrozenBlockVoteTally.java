package co.nyzo.verifier;

import co.nyzo.verifier.util.PrintUtil;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class FrozenBlockVoteTally {

    private long blockHeight;  // helpful for debugging; not necessary otherwise
    private Set<ByteBuffer> identifiersThatHaveVoted;
    private Map<ByteBuffer, Integer> voteMap;
    private Map<ByteBuffer, byte[]> hashLookup;
    private Map<ByteBuffer, Integer> cycleLengthLookup;

    public FrozenBlockVoteTally(long blockHeight) {
        this.blockHeight = blockHeight;
        this.identifiersThatHaveVoted = new HashSet<>();
        this.voteMap = new HashMap<>();
        this.hashLookup = new HashMap<>();
        this.cycleLengthLookup = new HashMap<>();
    }

    public void vote(byte[] identifier, byte[] hash, int cycleLength) {

        ByteBuffer identifierBuffer = ByteBuffer.wrap(identifier);
        if (!identifiersThatHaveVoted.contains(identifierBuffer)) {
            identifiersThatHaveVoted.add(identifierBuffer);

            ByteBuffer voteKey = voteKey(hash, cycleLength);
            Integer count = voteMap.get(voteKey);
            if (count == null) {
                count = 0;
            }

            count++;
            voteMap.put(voteKey, count);
            System.out.println("count is now " + count + " for hash " + PrintUtil.compactPrintByteArray(hash) +
                    " at height " + blockHeight);
            hashLookup.put(voteKey, hash);
            cycleLengthLookup.put(voteKey, cycleLength);
        }
    }

    private static ByteBuffer voteKey(byte[] hash, int cycleLength) {

        byte[] array = new byte[FieldByteSize.hash + FieldByteSize.cycleLength];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(hash);
        buffer.putInt(cycleLength);

        return buffer;
    }

    public int totalVotes() {

        int totalVotes = 0;
        for (Integer votes : voteMap.values()) {
            totalVotes += votes;
        }

        System.out.println("have " + totalVotes + " total votes at height " + blockHeight);
        return totalVotes;
    }

    public int votesForWinner(byte[] winnerHash, AtomicInteger cycleLength) {

        int votesForWinner = -1;
        for (ByteBuffer voteKey : voteMap.keySet()) {
            int votes = voteMap.get(voteKey);
            if (votes > votesForWinner) {
                votesForWinner = votes;
                System.arraycopy(hashLookup.get(voteKey), 0, winnerHash, 0, FieldByteSize.hash);
                cycleLength.set(cycleLengthLookup.get(voteKey));
            }
        }

        System.out.println(votesForWinner + " votes for winner " + PrintUtil.compactPrintByteArray(winnerHash) +
                ", height " + blockHeight + ", cycle length " + cycleLength.get());
        return votesForWinner;
    }
}
