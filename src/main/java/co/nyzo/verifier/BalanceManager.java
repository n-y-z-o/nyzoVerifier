package co.nyzo.verifier;

import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.TestnetUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class BalanceManager {

    private static final byte[] seedAccountIdentifier = ByteUtil.byteArrayFromHexString("12d454a69523f739-" +
            "eb5eb71c7deb8701-1804df336ae0e2c1-9e0b24a636683e31", FieldByteSize.identifier);
    private static final long initialSeedTransactionAmount = (TestnetUtil.testnet ? 500000L : 599L) *
            Transaction.micronyzoMultiplierRatio;  // 500,000 nyzos testnet, 599 nyzos production
    private static final long finalSeedTransactionAmount = TestnetUtil.testnet ? 1L :
            13758709L;  // 1 micronyzo testnet, 13.758709 nyzos production

    public static List<Transaction> approvedTransactionsForBlock(List<Transaction> transactions, Block previousBlock) {

        // Sort the transactions in block order, then remove all duplicates.
        sortTransactions(transactions);
        List<Transaction> dedupedTransactions = transactionsWithoutDuplicates(transactions);

        // Remove all transactions whose timestamps fall outside the block timestamp range. Because the array is sorted,
        // this can be done on the ends of the array.
        long blockHeight = previousBlock.getBlockHeight() + 1L;
        long startTimestamp = BlockManager.startTimestampForHeight(blockHeight);
        long endTimestamp = BlockManager.endTimestampForHeight(blockHeight);
        while (dedupedTransactions.size() > 0 && dedupedTransactions.get(0).getTimestamp() < startTimestamp) {
            dedupedTransactions.remove(0);
            System.out.println("removed transaction because timestamp was before beginning of block");
        }
        while (dedupedTransactions.size() > 0 &&
                dedupedTransactions.get(dedupedTransactions.size() - 1).getTimestamp() >= endTimestamp) {
            dedupedTransactions.remove(dedupedTransactions.size() - 1);
            System.out.println("removed transaction because timestamp was past end of block");
        }

        // If the block height is above zero, remove all transactions that are not seed or standard.
        if (blockHeight > 0) {
            for (int i = dedupedTransactions.size() - 1; i >= 0; i--) {
                if (dedupedTransactions.get(i).getType() != Transaction.typeSeed &&
                        dedupedTransactions.get(i).getType() != Transaction.typeStandard) {
                    dedupedTransactions.remove(i);
                    System.out.println("removed transaction because type is invalid");
                }
            }
        }

        // Remove all transactions less than 1 micronyzo.
        for (int i = dedupedTransactions.size() - 1; i >= 0; i--) {
            if (dedupedTransactions.get(i).getAmount() < 1L) {
                dedupedTransactions.remove(i);
                System.out.println("removed transaction at index " + i + " due to amount less than 1 micronyzo");
            }
        }

        // Check the previous-block hash here. Properly used, this provides protection against eclipse attacks.
        for (int i = dedupedTransactions.size() - 1; i >= 0; i--) {
            if (!dedupedTransactions.get(i).previousHashIsValid()) {
                dedupedTransactions.remove(i);
                System.out.println("removed transaction because previous hash was invalid");
            }
        }

        // Protect the seed-funding account from all transactions other than the transactions published on day 1.
        protectSeedFundingAccount(dedupedTransactions, blockHeight);

        // Remove any transactions with invalid signatures.
        for (int i = dedupedTransactions.size() - 1; i >= 0; i--) {
            if (!dedupedTransactions.get(i).signatureIsValid()) {
                dedupedTransactions.remove(i);
                System.out.println("removed transaction because signature was invalid");
            }
        }

        // Assemble the final list of transactions with valid amounts. This has to be done in ascending order of
        // timestamp, because older transactions take precedence over newer transactions.
        List<Transaction> approvedTransactions = new ArrayList<>();
        BalanceList balanceList = BalanceListManager.balanceListForBlock(previousBlock, null);
        Map<ByteBuffer, Long> identifierToBalanceMap = makeBalanceMap(balanceList);
        for (Transaction transaction : dedupedTransactions) {
            ByteBuffer senderIdentifier = ByteBuffer.wrap(transaction.getSenderIdentifier());
            Long senderBalance = identifierToBalanceMap.getOrDefault(senderIdentifier, 0L);
            if (transaction.getAmount() <= senderBalance || (transaction.getType() == Transaction.typeSeed &&
                    transaction.getFee() <= senderBalance)) {

                approvedTransactions.add(transaction);
                identifierToBalanceMap.put(senderIdentifier, senderBalance - transaction.getAmount());

                // Add the amount after fee to the receiver's account.
                long amountAfterFee = transaction.getAmount() - transaction.getFee();
                if (amountAfterFee > 0L) {
                    ByteBuffer receiverIdentifier = ByteBuffer.wrap(transaction.getReceiverIdentifier());
                    Long receiverBalance = identifierToBalanceMap.get(receiverIdentifier);
                    if (receiverBalance == null) {
                        receiverBalance = 0L;
                    }
                    receiverBalance += amountAfterFee;
                    identifierToBalanceMap.put(receiverIdentifier, receiverBalance);
                }
            } else {
                System.out.println("removed transaction because amount " + transaction.getAmount() + " was greater " +
                        "than balance " + senderBalance);
            }
        }

        return approvedTransactions;
    }

    public static Map<ByteBuffer, Long> makeBalanceMap(BalanceList balanceList) {

        Map<ByteBuffer, Long> balanceMap = new HashMap<>();
        if (balanceList != null) {
            for (BalanceListItem item : balanceList.getItems()) {
                balanceMap.put(ByteBuffer.wrap(item.getIdentifier()), item.getBalance());
            }
        }

        return balanceMap;
    }

    public static void sortTransactions(List<Transaction> transactions) {

        // First, sort transactions according to the acceptable ordering for blocks.
        Collections.sort(transactions, new Comparator<Transaction>() {
            @Override
            public int compare(Transaction transaction1, Transaction transaction2) {
                long timestamp1 = transaction1.getTimestamp();
                long timestamp2 = transaction2.getTimestamp();
                int result = 0;
                if (timestamp1 < timestamp2) {
                    result = -1;
                } else if (timestamp2 < timestamp1) {
                    result = 1;
                } else {
                    byte[] signature1 = transaction1.getSignature();
                    byte[] signature2 = transaction2.getSignature();
                    for (int i = 0; i < FieldByteSize.signature && result == 0; i++) {
                        int byte1 = signature1[i] & 0xff;
                        int byte2 = signature2[i] & 0xff;
                        if (byte1 < byte2) {
                            result = -1;
                        } else if (byte2 < byte1) {
                            result = 1;
                        }
                    }
                }

                return result;
            }
        });
    }

    public static List<Transaction> transactionsWithoutDuplicates(List<Transaction> sortedTransactions) {

        List<Transaction> transactionsWithoutDuplicates = new ArrayList<>();
        Transaction previousTransaction = null;
        for (Transaction transaction : sortedTransactions) {
            if (previousTransaction == null || !ByteUtil.arraysAreEqual(previousTransaction.getSignature(),
                    transaction.getSignature())) {
                transactionsWithoutDuplicates.add(transaction);
                previousTransaction = transaction;
            }
        }

        return transactionsWithoutDuplicates;
    }

    private static void protectSeedFundingAccount(List<Transaction> dedupedTransactions, long blockHeight) {

        // At block 1, 20% of the coins in the system were transferred to the seed-funding account. All of the seed
        // transactions were pre-signed, and the private key for the account was never saved. However, there is no
        // way to prove that the private key was not saved, so this logic provides assurance that the funds in that
        // account will only be used for the published seed transactions.
        for (int i = dedupedTransactions.size() - 1; i >= 0; i--) {
            Transaction transaction = dedupedTransactions.get(i);
            if (ByteUtil.arraysAreEqual(transaction.getSenderIdentifier(), seedAccountIdentifier)) {

                // These are the same parameters used to generate the transactions. In addition to transfers, funds
                // could be stolen from this account with large seed transactions or many smaller seed transactions.
                // We need to check all fields of the transaction, as they can all change the signature.
                long transactionIndex = blockHeight - SeedTransactionManager.lowestSeedTransactionHeight;

                long transactionAmount = finalSeedTransactionAmount + (initialSeedTransactionAmount -
                        finalSeedTransactionAmount) *
                        (SeedTransactionManager.totalSeedTransactions - transactionIndex - 1) /
                        (SeedTransactionManager.totalSeedTransactions - 1);
                long transactionTimestamp = BlockManager.getGenesisBlockStartTimestamp() + blockHeight *
                        Block.blockDuration + 1000L;

                boolean needToRemoveTransaction = false;
                if (transaction.getType() != Transaction.typeSeed) {
                    needToRemoveTransaction = true;
                    System.out.println("removed non-seed transaction from seed-funding account");
                }

                if (transaction.getAmount() != transactionAmount) {
                    needToRemoveTransaction = true;
                    System.out.println("removed seed transaction with incorrect amount: " +
                            PrintUtil.printAmount(transaction.getAmount()) + ", expected " +
                            PrintUtil.printAmount(transactionAmount));
                }

                if (transaction.getTimestamp() != transactionTimestamp) {
                    needToRemoveTransaction = true;
                    System.out.println("removed seed transaction with incorrect timestamp: " +
                            PrintUtil.printTimestamp(transaction.getTimestamp()) + ", expected " +
                            PrintUtil.printTimestamp(transactionTimestamp));
                }

                if (transaction.getSenderData().length > 0) {
                    needToRemoveTransaction = true;
                    System.out.println("removed seed transaction with non-empty sender data: " +
                            ByteUtil.arrayAsStringNoDashes(transaction.getSenderData()));
                }

                if (transaction.getPreviousHashHeight() != 0L) {
                    needToRemoveTransaction = true;
                    System.out.println("removed seed transaction with previous-hash height of " +
                            transaction.getPreviousHashHeight());
                }

                // Perform the actual removal, if necessary.
                if (needToRemoveTransaction) {
                    dedupedTransactions.remove(i);
                }
            }
        }
    }
}
