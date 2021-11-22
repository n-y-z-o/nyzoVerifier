package co.nyzo.verifier.tests;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.util.UpdateUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class CycleDigestTest {

    // This test compares the new CycleDigest class against the CycleInformation class. It also tests serialization and
    // deserialization of the CycleDigest class.

    public static void main(String[] args) {

        testHardCoded();

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
                    cycleDigest = CycleDigest.digestForNextBlock(cycleDigest, block.getVerifierIdentifier(),
                            block.getBlockHeight());

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
                            cycleDigest.getNewVerifierState() == NewVerifierState.NewVerifier);
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

                    // The digest should always be complete.
                    if (!cycleDigest.isComplete()) {
                        System.out.println("Cycle digest is not complete: " + cycleDigest);
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

    private static void testHardCoded() {
        // Dashes are only present to improve readability, and they are removed before processing. Expected values for
        // each digest are provided and checked. Hard-coded test cases like this are wonderful for examining behavior
        // granularly.

        // Tests these digests independently, and also compare them to one another. They should be identical.
        CycleDigest digest0 = testDigest("A-BA-BA-BA-BA", 15, new int[] { 2, 2, 2, 2 }, true,
                NewVerifierState.ExistingVerifier);
        CycleDigest digest1 = testDigest("BA-BA-BA-BA-BA", 15, new int[] { 2, 2, 2, 2 }, true,
                NewVerifierState.ExistingVerifier);
        check("digests 0 and 1", digest0, digest1);

        testDigest("BA-BA-BA-BA", 15, new int[] { 2, 2, 2, 2 }, false, NewVerifierState.ExistingVerifier);
        testDigest("A-BA-BA-BA", 6, new int[] { 2, 2, 2, 1 }, true, NewVerifierState.ExistingVerifier);
        testDigest("A-BA", 6, new int[] { 2, 1, 0, 0 }, false, NewVerifierState.ExistingVerifier);
        testDigest("A", 6, new int[] { 1, 0, 0, 0 }, false, NewVerifierState.Undetermined);
        testDigest("A-BA-BA-BA-BAC", 15, new int[] { 3, 2, 2, 2 }, true, NewVerifierState.ExistingVerifier);
    }

    private static CycleDigest testDigest(String digestString, int blockHeight, int[] expectedCycleLengths,
                                   boolean expectedComplete, NewVerifierState expectedNewVerifierState) {
        System.out.println("\ntesting digest " + digestString);

        // Remove the dashes. The dashes were only present to improve code readability, and they would interfere with
        // proper processing.
        String cleanDigestString = digestString.replace("-", "");

        // Build the cycle digest.
        char[] characters = cleanDigestString.toCharArray();
        List<ByteBuffer> identifiers = new ArrayList<>();
        for (char character : characters) {
            ByteBuffer identifier = ByteBuffer.wrap(new byte[] { (byte) character });
            identifiers.add(identifier);
        }
        CycleDigest cycleDigest = new CycleDigest(blockHeight, identifiers);

        // Check the block height, expected cycle lengths, and expectation of completness.
        check("block height, hardcoded " + digestString, blockHeight, cycleDigest.getBlockHeight());
        for (int i = 0; i < expectedCycleLengths.length; i++) {
            check("cycle length " + i + ", hardcoded " + digestString,
                    expectedCycleLengths[i], cycleDigest.getCycleLength(i));
        }
        check("completeness, hardcoded " + digestString, expectedComplete, cycleDigest.isComplete());

        System.out.println("built digest: " + cycleDigest);
        System.out.println("determination height: " + cycleDigest.getDeterminationHeight());

        return cycleDigest;
    }

    private static void check(String label, boolean value1, boolean value2) {
        if (value1 != value2) {
            System.out.println(ConsoleColor.Red.backgroundBright() + "mismatch (" + label + "): " + value1 + " != " +
                    value2 + ConsoleColor.reset);
            System.exit(1);
        }
    }

    private static void check(String label, int value1, int value2) {
        if (value1 != value2) {
            System.out.println(ConsoleColor.Red.backgroundBright() + "mismatch (" + label + "): " + value1 + " != " +
                    value2 + ConsoleColor.reset);
            System.exit(1);
        }
    }

    private static void check(String label, long value1, long value2) {
        if (value1 != value2) {
            System.out.println(ConsoleColor.Red.backgroundBright() + "mismatch (" + label + "): " + value1 + " != " +
                    value2 + ConsoleColor.reset);
            System.exit(1);
        }
    }

    private static void check(String label, Object object1, Object object2) {
        // To pass this check, nullity must be the same and, if non-null, the objects must be equal to one another. The
        // logic implements the negation of this, which is different nullity or unequal if non-null.
        if (((object1 == null) != (object2 == null)) || (object1 != null && !object1.equals(object2))) {
            System.out.println(ConsoleColor.Red.backgroundBright() + "mismatch (" + label + "): " + object1 + " != " +
                    object2 + ConsoleColor.reset);
            System.exit(1);
        }
    }
}
