package co.nyzo.verifier.tests;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.NewVerifierVote;
import co.nyzo.verifier.util.DebugUtil;
import co.nyzo.verifier.util.FileUtil;

import java.util.ArrayList;
import java.util.List;

public class ChainScoreTest {

    public static void main(String[] args) {

        String verifiers = "ABCDABCDABCDABCDEABCDEABCDEABFCDEAF".replace(" ", "");

        // NOTE: before running this script, delete the block root directory (sudo rm -r /var/lib/nyzo/blocks) and
        // recreate it with 777 permissions (sudo mkdir /var/lib/nyzo/blocks, sudo chmod 777 /var/lib/nyzo/blocks)

        Block previousBlock = null;
        long genesisStartTimestamp = 1000000L;
        BlockManager.setGenesisBlockStartTimestamp(genesisStartTimestamp);
        for (int i = 0; i < verifiers.length(); i++) {

            long height = i;
            byte[] previousBlockHash = previousBlock == null ? Block.genesisBlockHash : previousBlock.getHash();
            long startTimestamp = genesisStartTimestamp + Block.blockDuration * i;
            List<Transaction> transactions = new ArrayList<>();
            byte[] verifierSeed = HashUtil.doubleSHA256((verifiers.charAt(i) + "").getBytes());
            byte[] verifierIdentifier = KeyUtil.identifierForSeed(verifierSeed);
            BalanceList balanceList = Block.balanceListForNextBlock(previousBlock, transactions, verifierIdentifier);

            Block block = new Block(height, previousBlockHash, startTimestamp, transactions, balanceList.getHash(),
                    balanceList);
            block.sign(startTimestamp + 7000L, verifierSeed);

            // This is necessary for previous blocks to be found.
            UnfrozenBlockManager.registerBlock(block);

            previousBlock = block;



            CycleInformation cycle = block.getCycleInformation();
            System.out.println(String.format("block%s%2d (%s): c=%2d, n=%s, d=%s, s=%d, w=%d",
                    block.getCycleInformation().isGenesisCycle() ? "*" : " ", i, verifiers.charAt(i) + "",
                    cycle.getCycleLength(), cycle.isNewVerifier() ? "Y" : "N", block.getContinuityState() + "",
                    block.chainScore(Math.max(block.getBlockHeight() - 1, 0L)),
                    block.getCycleInformation().getWindowStartHeight()));
        }
    }



}
