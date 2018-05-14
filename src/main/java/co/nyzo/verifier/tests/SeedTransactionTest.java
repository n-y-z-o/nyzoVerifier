package co.nyzo.verifier.tests;

import co.nyzo.verifier.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SeedTransactionTest {

    public static void main(String[] args) throws Exception {

        Verifier.loadGenesisBlock();
        SeedTransactionManager.start();

        Block previousBlock = BlockManager.frozenBlockForHeight(0L);
        for (int i = 0; i < 100; i++) {

            Transaction transaction = SeedTransactionManager.transactionForBlock(previousBlock.getBlockHeight() + 1);
            List<Transaction> approvedTransactions;
            if (transaction == null) {
                System.out.println("transaction is null for block " + i);
                approvedTransactions = new ArrayList<>();
            } else {
                System.out.println("transaction for block " + i + " is of the amount " + transaction.getAmount());
                List<Transaction> transactions = Collections.singletonList(transaction);
                approvedTransactions = BalanceManager.approvedTransactionsForBlock(transactions, previousBlock);
                System.out.println(approvedTransactions.size() + " approved transactions");
            }

            System.out.println("i=" + i + ", previous block height=" + previousBlock.getBlockHeight());

            BalanceList balanceList = Block.balanceListForNextBlock(previousBlock, approvedTransactions,
                    Verifier.getIdentifier());
            previousBlock = new Block(previousBlock.getBlockHeight() + 1, previousBlock.getPreviousBlockHash(),
                    previousBlock.getStartTimestamp() + Block.blockDuration, approvedTransactions,
                    balanceList.getHash(), balanceList);

            Thread.sleep(1000L);
        }
    }
}
