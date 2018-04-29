package co.nyzo.verifier;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FrozenBlockVoteTally {

    private Set<ByteBuffer> identifiersThatHaveVoted;
    private Map<ByteBuffer, Integer> votes;

    public FrozenBlockVoteTally() {
        this.identifiersThatHaveVoted = new HashSet<>();
        this.votes = new HashMap<>();
    }

    public boolean vote(byte[] identifier, byte[] hash) {

        ByteBuffer identifierBuffer = ByteBuffer.wrap(identifier);
        ByteBuffer hashBuffer = ByteBuffer.wrap(hash);
        Integer count = votes.get(hashBuffer);
        if (count == null) {
            count = 0;
        }

        if (!identifiersThatHaveVoted.contains(identifierBuffer)) {
            identifiersThatHaveVoted.add(identifierBuffer);
            count++;
            votes.put(hashBuffer, count);
            System.out.println("count is now " + count + " for hash " + ByteUtil.arrayAsStringWithDashes(hash));
        }

        return count >= NodeManager.numberOfNodesInMesh() / 2;
    }
}
