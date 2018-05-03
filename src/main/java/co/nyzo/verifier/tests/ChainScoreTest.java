package co.nyzo.verifier.tests;

import co.nyzo.verifier.*;

import java.util.ArrayList;
import java.util.List;

public class ChainScoreTest {

    public static void main(String[] args) {

        String[] verifiers =    { "A", "B", "C", "B", "C", "A", "C", "A", "C", "A", "B", "C", "A", "B", "C", "D", "D",
                "A" };
        long[] expectedScores = {   0,   0,   0,  10,  10,   0,  10,  10,  10,  10,   0,   0,   0,   0,   0, -10,  20,
                1000020 };

        Block previousBlock = null;
        long genesisStartTimestamp = 1000000L;
        for (int i = 0; i < verifiers.length; i++) {

            long height = i;
            byte[] previousBlockHash = previousBlock == null ? Block.genesisBlockHash : previousBlock.getHash();
            long startTimestamp = genesisStartTimestamp + Block.blockDuration * i;
            List<Transaction> transactions = new ArrayList<>();
            byte[] verifierSeed = HashUtil.doubleSHA256(verifiers[i].getBytes());
            byte[] verifierIdentifier = KeyUtil.identifierForSeed(verifierSeed);
            BalanceList balanceList = Block.balanceListForNextBlock(previousBlock, transactions, verifierIdentifier);

            Block block = new Block(height, previousBlockHash, startTimestamp, transactions, balanceList.getHash(),
                    balanceList);
            block.sign(verifierSeed);
            block.setPreviousBlock(previousBlock);

            previousBlock = block;

            long chainScore = block.chainScore(0L);
            CycleInformation cycle = block.getCycleInformation();
            System.out.println(String.format("block %2d (%s): score=%2d (%s), cycle length=%2d, new=%s, " +
                    "cycle index=%2d, discontinuity state=%s", i, verifiers[i], chainScore,
                    chainScore == expectedScores[i] ? "pass" : "FAIL", cycle.getCycleLength(),
                    cycle.isNewVerifier() ? "Y" : "N", cycle.getVerifierIndexInCycle(),
                    block.getDiscontinuityState() + ""));
        }
    }



}
