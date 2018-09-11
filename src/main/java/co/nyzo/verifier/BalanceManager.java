package co.nyzo.verifier;

import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class BalanceManager {

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

        // Check the previous-block hash here. Properly used, this provides protection against eclipse attacks.
        for (int i = dedupedTransactions.size() - 1; i >= 0; i--) {
            if (!dedupedTransactions.get(i).previousHashIsValid()) {
                dedupedTransactions.remove(i);
                System.out.println("removed transaction because previous hash was invalid");
            }
        }

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
}
