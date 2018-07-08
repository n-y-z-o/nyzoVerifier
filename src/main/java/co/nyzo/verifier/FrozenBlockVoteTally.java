package co.nyzo.verifier;

import co.nyzo.verifier.util.PrintUtil;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class FrozenBlockVoteTally {

    private long blockHeight;  // helpful for debugging; not necessary otherwise
    private Set<ByteBuffer> identifiersThatHaveVoted;
    private Map<ByteBuffer, Integer> voteMap;
    private Map<ByteBuffer, byte[]> hashMap;
    private Map<ByteBuffer, Long> startHeightMap;

    public FrozenBlockVoteTally(long blockHeight) {
        this.blockHeight = blockHeight;
        this.identifiersThatHaveVoted = new HashSet<>();
        this.voteMap = new HashMap<>();
        this.hashMap = new HashMap<>();
        this.startHeightMap = new HashMap<>();
    }

    public void vote(byte[] identifier, byte[] hash, long startHeight) {

        ByteBuffer identifierBuffer = ByteBuffer.wrap(identifier);
        if (!identifiersThatHaveVoted.contains(identifierBuffer)) {
            identifiersThatHaveVoted.add(identifierBuffer);

            ByteBuffer voteKey = voteKey(hash, startHeight);
            Integer count = voteMap.get(voteKey);
            if (count == null) {
                count = 0;
            }

            count++;
            voteMap.put(voteKey, count);
            System.out.println("count is now " + count + " for hash " + PrintUtil.compactPrintByteArray(hash) +
                    " at height " + blockHeight);
            hashMap.put(voteKey, hash);
            startHeightMap.put(voteKey, startHeight);
        }
    }

    private static ByteBuffer voteKey(byte[] hash, long startHeight) {

        byte[] array = new byte[FieldByteSize.hash + FieldByteSize.blockHeight];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(hash);
        buffer.putLong(startHeight);

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

    public int votesForWinner(byte[] winnerHash, AtomicLong startHeight) {

        int votesForWinner = -1;
        for (ByteBuffer voteKey : voteMap.keySet()) {
            int votes = voteMap.get(voteKey);
            if (votes > votesForWinner) {
                votesForWinner = votes;
                System.arraycopy(hashMap.get(voteKey), 0, winnerHash, 0, FieldByteSize.hash);
                startHeight.set(startHeightMap.get(voteKey));
            }
        }

        System.out.println(votesForWinner + " votes for winner " + PrintUtil.compactPrintByteArray(winnerHash) +
                ", height " + blockHeight + ", start height " + startHeight.get());
        return votesForWinner;
    }
}
