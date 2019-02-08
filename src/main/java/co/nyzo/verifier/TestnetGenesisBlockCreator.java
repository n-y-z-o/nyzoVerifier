package co.nyzo.verifier;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.FileUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestnetGenesisBlockCreator {

    public static final byte[] nullBlockHash = HashUtil.doubleSHA256(new byte[0]);

    private static final byte[] verifierSeed = ByteUtil.byteArrayFromHexString("0101010101010101-0101010101010101-" +
            "0101010101010101-0101010101010101", FieldByteSize.seed);

    public static void main(String[] args) {

        long genesisTimestamp = nextGenesisTimestamp();

        // Delete the existing blockchain.
        FileUtil.delete(BlockManager.blockRootDirectory);

        byte[] verifierIdentifier = KeyUtil.identifierForSeed(verifierSeed);

        List<Transaction> transactions = new ArrayList<>();
        transactions.add(Transaction.coinGenerationTransaction(genesisTimestamp + 1000L,
                Transaction.micronyzosInSystem, verifierIdentifier));

        // Create the balance list and the block.
        BalanceList balanceList = Block.balanceListForNextBlock(null, null, transactions,
                verifierIdentifier);
        Block block = new Block(0, nullBlockHash, genesisTimestamp, transactions, balanceList.getHash());
        block.sign(genesisTimestamp, verifierSeed);
        System.out.println("block " + 0 + " is valid: " + block.signatureIsValid());

        BlockManager.writeBlocksToFile(Collections.singletonList(block), Collections.singletonList(balanceList),
                BlockManager.individualFileForBlockHeight(0));
    }

    private static long nextGenesisTimestamp() {

        long currentTime = System.currentTimeMillis();
        long increment = 1000L * 60L * 1L;  // align with a 1-minute interval
        long nextIncrement = (currentTime / increment) * increment + increment;

        long minimumDelay = 1000L * 30L;  // ensure that the minimum delay is 30 seconds
        if (nextIncrement - currentTime < minimumDelay) {
            nextIncrement += increment;
        }

        System.out.println(String.format("next increment is in %.1f seconds (%.1f minutes) at %d",
                (nextIncrement - currentTime) / 1000.0, (nextIncrement - currentTime) / (60 * 1000.0),
                nextIncrement));

        return nextIncrement;
    }
}
