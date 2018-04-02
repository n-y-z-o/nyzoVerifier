package co.nyzo.verifier;

import java.util.HashMap;
import java.util.Map;

public class PreviousHashManager {

    // This class exists to provide efficient verification of previous-block hashes.
    private static long latestHashHeight = 0;
    private static final Map<Long, byte[]> heightToHashMap = new HashMap<>();

    static {

    }

    public static boolean previousHashIsValid(long blockHeight, byte[] blockHash) {

        boolean valid = false;
        if (blockHeight <= latestHashHeight) {
            byte[] correctHash = hashForHeight(blockHeight);
            valid = correctHash != null && ByteUtil.arraysAreEqual(correctHash, blockHash);
        }

        return valid;
    }

    public static long latestHashHeight() {
        return latestHashHeight;
    }

    public static byte[] hashForHeight(long blockHeight) {

        byte[] result = null;
        if (blockHeight <= latestHashHeight) {
            result = heightToHashMap.get(blockHeight);
            if (result == null) {
                Block block = Block.fromFile(blockHeight);
                if (block != null) {
                    result = block.getHash();
                }
            }
        }

        return result;
    }

    public static void addHash(long height, byte[] hash) {
        heightToHashMap.put(height, hash);
        if (height > latestHashHeight) {
            latestHashHeight = height;
        }
    }
}
