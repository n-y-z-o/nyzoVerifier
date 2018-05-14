package co.nyzo.verifier.tests;

import co.nyzo.verifier.SeedTransactionManager;
import co.nyzo.verifier.Transaction;
import co.nyzo.verifier.Verifier;

public class SeedTransactionTest {

    public static void main(String[] args) throws Exception {

        Verifier.loadGenesisBlock();
        SeedTransactionManager.start();

        for (int i = 0; i < 100; i++) {

            Transaction transaction = SeedTransactionManager.transactionForBlock(i);
            if (transaction == null) {
                System.out.println("transaction is null for block " + i);
            } else {
                System.out.println("transaction for block " + i + " is of the amount " + transaction.getAmount());
            }

            Thread.sleep(1000L);
        }
    }
}
