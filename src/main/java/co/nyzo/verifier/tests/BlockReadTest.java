package co.nyzo.verifier.tests;

import co.nyzo.verifier.Block;
import co.nyzo.verifier.BlockManager;
import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.util.UpdateUtil;

public class BlockReadTest {

    public static void main(String[] args) {

        long blocksPerYear = 17280L * 365L;
        int numberThatExist = 0;
        int numberThatDoNotExist = 0;
        for (int i = 0; i < blocksPerYear; i += 1000L) {
            if (BlockManager.fileForBlockHeight(i, "yo").exists()) {
                numberThatExist++;
            } else {
                numberThatDoNotExist++;
            }
        }
        System.out.println("number that exist: " + numberThatExist);
        System.out.println("number that do not exist: " + numberThatDoNotExist);

        Block genesisBlock = BlockManager.frozenBlockForHeight(0L);
        if (genesisBlock == null) {
            System.out.println("Genesis block is null");
        } else {
            System.out.println("Genesis block hash: " + ByteUtil.arrayAsStringWithDashes(genesisBlock.getHash()));
        }

        UpdateUtil.terminate();

    }
}
