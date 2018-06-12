package co.nyzo.verifier.tests;

import co.nyzo.verifier.Block;
import co.nyzo.verifier.BlockManager;
import co.nyzo.verifier.util.PrintUtil;

public class BlockPrint {

    // This class is used to display basic information about a block from the console.

    public static void main(String[] args) {

        long height = 0L;
        try {
            height = Long.parseLong(args[0]);
        } catch (Exception ignored) { }

        System.out.println("height:              " + height);
        Block block = BlockManager.frozenBlockForHeight(height);
        if (block == null) {
            System.out.println("*** block is null ***");
        } else {

            System.out.println("height:                   " + height);
            System.out.println("number of transactions:   " + block.getTransactions().size());
            System.out.println("verifier:                 " +
                    PrintUtil.compactPrintByteArray(block.getVerifierIdentifier()));
            System.out.println("signature:                " +
                    PrintUtil.compactPrintByteArray(block.getVerifierSignature()));
        }
    }
}
