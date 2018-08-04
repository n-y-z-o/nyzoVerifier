package co.nyzo.verifier;

public class BlockVoteTally {

    // There is a lot of redundancy in this class, but it's cheap redundancy, and it makes the vote-tabulation process
    // easier to understand.

    private long height;
    private byte[] blockHash;
    private int numberOfHashVotes;
    private int numberOfCancelledVotes;
    private int threshold;

    public BlockVoteTally(long height, byte[] blockHash, int numberOfHashVotes, int numberOfCancelledVotes,
                          int threshold) {

        this.height = height;
        this.blockHash = blockHash;
        this.numberOfHashVotes = numberOfHashVotes;
        this.numberOfCancelledVotes = numberOfCancelledVotes;
        this.threshold = threshold;
    }

    public long getHeight() {
        return height;
    }

    public byte[] getBlockHash() {
        return blockHash;
    }

    public int getNumberOfHashVotes() {
        return numberOfHashVotes;
    }

    public int getNumberOfCancelledVotes() {
        return numberOfCancelledVotes;
    }

    public int getThreshold() {
        return threshold;
    }
}
