package co.nyzo.verifier.tests;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.UpdateUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;

public class CycleDigestTest {

    // This test compares the new CycleDigest class against the CycleInformation class. It also tests serialization and
    // deserialization of the CycleDigest class.

    public static void main(String[] args) {

        CycleDigest cycleDigest = null;
        boolean missingFile = false;
        BalanceList balanceList;
        int fileIndex = 0;
        while (!missingFile) {
            long startBlockHeight = fileIndex * BlockManager.blocksPerFile;
            File file = BlockManager.consolidatedFileForBlockHeight(startBlockHeight);
            if (file.exists()) {
                // Load the blocks from the file into the BlockManagerMap.
                long endBlockHeight = startBlockHeight + BlockManager.blocksPerFile - 1L;
                List<Block> blocks = BlockManager.loadBlocksInFile(file, startBlockHeight, endBlockHeight);
                for (Block block : blocks) {

                    BlockManager.setFrozenEdge(block, null);

                    // Get the cycle information and cycle digest for the block.
                    CycleInformation cycleInformation = block.getCycleInformation();
                    cycleDigest = CycleDigest.digestForNextBlock(cycleDigest, block.getVerifierIdentifier());

                    // Check the cycle length and all 4 parameterized cycle lengths.
                    check("cycle length, block " + block.getBlockHeight(), cycleInformation.getCycleLength(),
                            cycleDigest.getCycleLength());
                    for (int i = 0; i < 4; i++) {
                        check("cycle length (" + i + "), block " + block.getBlockHeight(),
                                cycleInformation.getCycleLength(i), cycleDigest.getCycleLength(i));
                    }

                    // Check the maximum cycle length and determination height.
                    check("maximum cycle length, block " + block.getBlockHeight(),
                            cycleInformation.getMaximumCycleLength(), cycleDigest.getMaximumCycleLength());
                    check("determination height, block " + block.getBlockHeight(),
                            cycleInformation.getDeterminationHeight(), cycleDigest.getDeterminationHeight());

                    // Check the new-verifier and Genesis-cycle flags.
                    check("new verifier, block " + block.getBlockHeight(), cycleInformation.isNewVerifier(),
                            cycleDigest.isNewVerifier());
                    check("Genesis cycle, block " + block.getBlockHeight(), cycleInformation.isInGenesisCycle(),
                            cycleDigest.isInGenesisCycle());

                    // Serialize the CycleDigest, deserialize into a new copy, and compare.
                    byte[] serializedDigest = cycleDigest.getBytes();
                    CycleDigest deserializedDigest = CycleDigest.fromByteBuffer(ByteBuffer.wrap(serializedDigest));
                    if (!cycleDigest.equals(deserializedDigest)) {
                        System.out.println("mismatch of original and deserialized CycleDigest at block height " +
                                block.getBlockHeight());
                        System.exit(1);
                    }

                    System.out.println("PASSED: " + block);
                }
            } else {
                missingFile = true;
            }

            fileIndex++;
        }

        UpdateUtil.terminate();
    }

    private static void check(String label, boolean value1, boolean value2) {
        if (value1 != value2) {
            System.out.println("mismatch (" + label + "): " + value1 + " != " + value2);
            System.exit(1);
        }
    }

    private static void check(String label, int value1, int value2) {
        if (value1 != value2) {
            System.out.println("mismatch (" + label + "): " + value1 + " != " + value2);
            System.exit(1);
        }
    }

    private static void check(String label, long value1, long value2) {
        if (value1 != value2) {
            System.out.println("mismatch (" + label + "): " + value1 + " != " + value2);
            System.exit(1);
        }
    }
}
