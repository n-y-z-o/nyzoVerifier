package co.nyzo.verifier;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class FrozenBlockVoteTally {

    private Map<ByteBuffer, Integer> votes;

    public FrozenBlockVoteTally() {
        this.votes = new HashMap<>();
    }

    public boolean vote(byte[] hash) {

        ByteBuffer buffer = ByteBuffer.wrap(hash);
        Integer count = votes.get(buffer);
        if (count == null) {
            count = 0;
        }
        count++;
        votes.put(buffer, count);

        System.out.println("count is now " + count + " for hash " + ByteUtil.arrayAsStringWithDashes(hash));

        return count >= NodeManager.numberOfNodesInMesh() / 2;
    }
}
