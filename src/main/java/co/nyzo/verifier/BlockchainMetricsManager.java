package co.nyzo.verifier;

import co.nyzo.verifier.util.LogUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BlockchainMetricsManager {

    // The baseline transaction rate is 10 transactions per second, which is 70 transactions per block.
    private static final int baselineTransactionRate = 10;
    private static final int baselineTransactionsPerBlock = (int) (Block.blockDuration * baselineTransactionRate /
            1000L);

    // Above baseline, one transaction is allowed per block for each Nyzo in organic transactions, on average, in the
    // previous cycle. This ensures that transaction capacity automatically increases to support additional demand on
    // the system while eliminating the possibility of cheap attacks with many small transactions. To enable this
    // behavior, the sum of transactions over the previous cycle must be tracked.
    private static final Map<Long, Long> blockTransactionSumMap = new ConcurrentHashMap<>();
    private static long cycleTransactionSum = 0L;
    private static int cycleLength = 1;

    public static int maximumTransactionsForBlockAssembly() {
        int additionalTransactions = (int) (cycleTransactionSum / Transaction.micronyzoMultiplierRatio / cycleLength);
        return baselineTransactionsPerBlock + Math.max(additionalTransactions, 0);
    }

    public static void registerBlock(Block block) {

        // Store the block transaction sum in the map.
        long blockSum = standardTransactionSum(block.getTransactions());
        blockTransactionSumMap.put(block.getBlockHeight(), blockSum);

        // Calculate the cycle sum and, in the same process, remove old values from the map.
        long cycleTransactionSum = 0L;
        if (block.getCycleInformation() != null) {
            long thresholdHeight = block.getBlockHeight() - block.getCycleInformation().getCycleLength();
            for (Long height : new HashSet<>(blockTransactionSumMap.keySet())) {
                if (height < thresholdHeight) {
                    blockTransactionSumMap.remove(height);
                } else {
                    cycleTransactionSum += blockTransactionSumMap.get(height);
                }
            }

            // Store the cycle length.
            cycleLength = block.getCycleInformation().getCycleLength();
        }

        // Store the cycle sum.
        BlockchainMetricsManager.cycleTransactionSum = cycleTransactionSum;
        LogUtil.println("after registering block " + block + " in BlockchainMetricsManager(), cycleTransactionSum=" +
                PrintUtil.printAmountWithCommas(BlockchainMetricsManager.cycleTransactionSum) + ", cycleLength=" +
                cycleLength);
    }

    private static long standardTransactionSum(List<Transaction> transactions) {

        long sum = 0L;
        for (Transaction transaction : transactions) {
            if (transaction.getType() == Transaction.typeStandard) {
                sum += transaction.getAmount();
            }
        }

        return sum;
    }
}
