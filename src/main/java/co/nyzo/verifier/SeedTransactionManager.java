package co.nyzo.verifier;

import co.nyzo.verifier.util.PrintUtil;

import java.util.ArrayList;
import java.util.List;

public class SeedTransactionManager {

    private static final long blocksPerDay = 12 * 60 * 24;
    private static final long startHeight = blocksPerDay;  // start one day after the blockchain starts
    private static final long numberOfTransactions = blocksPerDay * 365;  // one year of seed transactions

    public static void main(String[] args) {

        generateTransactions(new byte[FieldByteSize.seed], Transaction.micronyzosInSystem / 5L,
                BlockManager.frozenBlockForHeight(0L));
    }

    private static void generateTransactions(byte[] signerSeed, long targetFees, Block genesisBlock) {

        byte[] identifier = KeyUtil.identifierForSeed(signerSeed);
        long previousHashHeight = 0L;
        long genesisBlockTimestamp = genesisBlock.getStartTimestamp();
        byte[] genesisBlockHash = genesisBlock.getHash();

        List<Transaction> transactions = new ArrayList<>();

        long remainingFees = targetFees;
        long feesForMonth = 0L;
        long[] breakpointHeights = breakpointHeights();  // these are the "months", which are 30/31 day periods

        long previousFee = 0L;
        int month = 0;
        int indexInMonth = 0;
        double[] percentages = { 12.9, 12.4, 11.8, 11.1, 10.3, 9.4, 8.4, 7.3, 6.1, 4.8, 3.4, 1.9 };
        long processStartTime = System.currentTimeMillis();
        for (long i = 0; i < numberOfTransactions; i++) {

            long height = startHeight + i;
            long timestamp = genesisBlockTimestamp + height * Block.blockDuration + 1000L;
            double multiplier = percentages[month] * 400.0 / 100.0 / blocksPerDay / 30.390005;
            long transactionAmount = Math.max((long) (targetFees * multiplier), 1L);
            if (month == 11 && indexInMonth < 439040) {
                transactionAmount += 400L;
            }
            byte[] senderData = new byte[FieldByteSize.hash];

            Transaction transaction = Transaction.seedTransaction(timestamp, transactionAmount, identifier,
                    previousHashHeight, genesisBlockHash, senderData, signerSeed);
            long fee = transaction.getFee();
            if (fee > previousFee) {
                System.out.println(fee + " > " + previousFee);
            }
            previousFee = fee;
            remainingFees -= fee;
            feesForMonth += fee;
            indexInMonth++;

            if (i == breakpointHeights[month]) {
                month++;
                System.out.println("after transaction " + pad(i) + ", remaining fees=" +
                        PrintUtil.printAmount(remainingFees) + String.format(", %.2f%%", feesForMonth * 100.0 /
                        targetFees) + " of fees distributed this month, took " +
                        ((System.currentTimeMillis() - processStartTime) / 1000) + " seconds to process");
                feesForMonth = 0L;
                indexInMonth = 0;
            }
        }

        System.out.println("after last transaction remaining fees=" + PrintUtil.printAmount(remainingFees));

    }

    private static long[] breakpointHeights() {

        long[] heights = new long[12];

        long offset = 0L;
        for (int i = 0; i < 11; i++) {
            offset += i % 2 == 1 ? 31 : 30;
            heights[i] = offset * blocksPerDay - 1;
        }
        heights[11] = numberOfTransactions - 1;

        return heights;
    }

    private static String pad(long value) {

        String result = value + "";
        while (result.length() < 8) {
            result = " " + result;
        }

        return result;
    }
}
