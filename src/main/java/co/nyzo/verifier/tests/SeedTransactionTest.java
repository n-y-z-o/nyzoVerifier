package co.nyzo.verifier.tests;

import co.nyzo.verifier.SeedTransactionManager;
import co.nyzo.verifier.Transaction;

public class SeedTransactionTest {

    public static void main(String[] args) throws Exception {

        for (int i = 0; i < 5000; i++) {

            Transaction transaction = SeedTransactionManager.transactionForBlock(i);
            if (transaction == null) {
                System.out.println("transaction is null");
            } else {
                System.out.println("transaction for block " + i + " is of the amount " + transaction.getAmount());
            }

            Thread.sleep(3000L);
        }
    }
}
