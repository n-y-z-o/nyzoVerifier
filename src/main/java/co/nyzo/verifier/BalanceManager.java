package co.nyzo.verifier;

import java.nio.ByteBuffer;
import java.util.*;

public class BalanceManager {

    public static void main(String[] args) {

        Random random = new Random();
        List<Transaction> transactions = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            int timestamp1 = 100; random.nextInt(10000000);
            int timestamp2 = 100; random.nextInt(10000000);
            if (i == 7 || i == 9) {
                timestamp2 = timestamp1;
            }

            byte[] signature1 = new byte[FieldByteSize.signature];
            byte[] signature2 = new byte[FieldByteSize.signature];
            for (int j = 0; j < FieldByteSize.signature; j++) {
                signature1[j] = (byte) random.nextInt(256);
                signature2[j] = (byte) random.nextInt(256);
                if (i == 9) {
                    signature2[j] = signature1[j];
                }
            }

            // long timestamp, long amount, byte[] receiverIdentifier,
            // byte[] recentBlockHash, byte[] senderIdentifier,
            // byte[] senderData, byte[] signature
            transactions.add(Transaction.standardTransaction(timestamp1, 0, new byte[32], 0L, new byte[32],
                    new byte[32], new byte[32], signature1));
            transactions.add(Transaction.standardTransaction(timestamp2, 0, new byte[32], 0L, new byte[32],
                    new byte[32], new byte[32], signature2));
        }

        sortTransactions(transactions);

        System.out.println("*** sorted transactions ***");
        int index = 0;
        for (Transaction transaction : transactions) {
            System.out.println(String.format("%3d, %10d, %s", index++, transaction.getTimestamp(),
                    ByteUtil.arrayAsStringWithDashes(transaction.getSignature())));
        }

        System.out.println("*** transactions without duplicates ***");
        index = 0;
        List<Transaction> transactionsWithoutDuplicates = transactionsWithoutDuplicates(transactions);
        for (Transaction transaction : transactionsWithoutDuplicates) {
            System.out.println(String.format("%3d, %10d, %s", index++, transaction.getTimestamp(),
                    ByteUtil.arrayAsStringWithDashes(transaction.getSignature())));
        }
    }

    public static List<Transaction> approvedTransactionsForBlock(List<Transaction> transactions, long blockHeight) {

        // Sort the transactions in block order, then remove all duplicates.
        sortTransactions(transactions);
        List<Transaction> dedupedTransactions = transactionsWithoutDuplicates(transactions);

        // Remove all transactions whose timestamps fall outside the block timestamp range. Because the array is sorted,
        // this can be done on the ends of the array.
        long startTimestamp = BlockManager.startTimestampForHeight(blockHeight);
        long endTimestamp = BlockManager.endTimestampForHeight(blockHeight);
        while (dedupedTransactions.size() > 0 && dedupedTransactions.get(0).getTimestamp() < startTimestamp) {
            dedupedTransactions.remove(0);
        }
        while (dedupedTransactions.size() > 0 &&
                dedupedTransactions.get(dedupedTransactions.size() - 1).getTimestamp() >= endTimestamp) {
            dedupedTransactions.remove(dedupedTransactions.size() - 1);
        }

        // If the block height is above zero, remove all transactions that are not seed or standard.
        if (blockHeight > 0) {
            for (int i = dedupedTransactions.size() - 1; i >= 0; i--) {
                if (dedupedTransactions.get(i).getType() != Transaction.typeSeed &&
                        dedupedTransactions.get(i).getType() != Transaction.typeStandard) {
                    dedupedTransactions.remove(i);
                }
            }
        }

        // Check the previous-block hash here. Properly used, this provides protection against eclipse attacks.
        for (int i = dedupedTransactions.size() - 1; i >= 0; i--) {
            if (!dedupedTransactions.get(i).previousHashIsValid()) {
                dedupedTransactions.remove(i);
            }
        }

        // Remove any transactions with invalid signatures.
        for (int i = dedupedTransactions.size() - 1; i >= 0; i--) {
            if (!dedupedTransactions.get(i).signatureIsValid()) {
                dedupedTransactions.remove(i);
            }
        }

        // Assemble the final list of transactions with valid amounts. This has to be done in ascending order of
        // timestamp, because older transactions take precedence over newer transactions.
        List<Transaction> approvedTransactions = new ArrayList<>();
        Map<ByteBuffer, Long> identifierToBalanceMap = balancesAtEndOfBlock(blockHeight - 1);
        for (Transaction transaction : dedupedTransactions) {
            ByteBuffer senderIdentifier = ByteBuffer.wrap(transaction.getSenderIdentifier());
            Long senderBalance = identifierToBalanceMap.get(senderIdentifier);
            if (senderBalance != null && transaction.getAmount() <= senderBalance) {
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
            }
        }

        return approvedTransactions;
    }

    private static void sortTransactions(List<Transaction> transactions) {

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

    private static List<Transaction> transactionsWithoutDuplicates(List<Transaction> sortedTransactions) {

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

    private static Map<ByteBuffer, Long> balancesAtEndOfBlock(long blockHeight) {

        Map<ByteBuffer, Long> balances = new HashMap<>();
        BalanceList balanceList = BalanceList.fromFile(blockHeight);
        if (balanceList != null) {
            for (BalanceListItem item : balanceList.getItems()) {
                balances.put(ByteBuffer.wrap(item.getIdentifier()), item.getBalance());
            }
        }

        return balances;
    }
}
