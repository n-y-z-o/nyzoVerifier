package co.nyzo.verifier;

import java.util.concurrent.atomic.AtomicLong;

public class BlockManager {

    public static final byte[] genesisBlockVerifierIdentifier = ByteUtil.byteArrayFromHexString("302a300506032b65-" +
            "700321006b32332d-4b28e6add7b8f86f-374045cafc645334", 32);

    private static final AtomicLong highestBlockFrozen = new AtomicLong(-1L);

    static {
        loadBlocks();
    }

    public static long highestBlockFrozen() {
        return highestBlockFrozen.get();
    }

    private static void loadBlocks() {

        // TODO: also load from network here

        if (Block.fileForBlockHeight(0L).exists()) {
            long highIndex = 1L;
            while (Block.fileForBlockHeight(highIndex).exists()) {
                highIndex *= 2L;
            }

            long lowIndex = 0L;
            while (lowIndex < highIndex) {
                long middleIndex = (lowIndex + highIndex + 1L) / 2L;
                if (Block.fileForBlockHeight(middleIndex).exists()) {
                    lowIndex = middleIndex;
                } else {
                    highIndex = middleIndex - 1;
                }
            }

            highestBlockFrozen.set(lowIndex);
        }
    }

    public static void setHighestBlockFrozen(long height) {

        if (height < highestBlockFrozen.get()) {
            System.err.println("Setting highest block frozen to a lesser value than is currently set.");
        }

        highestBlockFrozen.set(height);
    }

    public static long heightForTimestamp(long timestamp) {

        return (timestamp - Block.genesisBlockStartTimestamp) / Block.blockDuration;
    }

    public static long startTimestampForHeight(long blockHeight) {

        return Block.genesisBlockStartTimestamp + blockHeight * Block.blockDuration;
    }

    public static long endTimestampForHeight(long blockHeight) {

        return Block.genesisBlockStartTimestamp + (blockHeight + 1L) * Block.blockDuration;
    }

    public static long highestBlockOpenForProcessing() {

        // A block is considered open for processing 3 seconds after it completes, which is 8 seconds after it starts.
        return (System.currentTimeMillis() - 8000L - Block.genesisBlockStartTimestamp) / Block.blockDuration;
    }
}
