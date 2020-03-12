package co.nyzo.verifier;

import co.nyzo.verifier.messages.CycleTransactionSignature;
import co.nyzo.verifier.util.LogUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.TestnetUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class BalanceManager {

    public static final byte[] seedAccountIdentifier = ByteUtil.byteArrayFromHexString("12d454a69523f739-" +
            "eb5eb71c7deb8701-1804df336ae0e2c1-9e0b24a636683e31", FieldByteSize.identifier);
    private static final long initialSeedTransactionAmount = (TestnetUtil.testnet ? 500000L : 599L) *
            Transaction.micronyzoMultiplierRatio;  // 500,000 nyzos testnet, 599 nyzos production
    private static final long finalSeedTransactionAmount = TestnetUtil.testnet ? 1L :
            13758709L;  // 1 micronyzo testnet, 13.758709 nyzos production

    public static final long minimumPreferredBalance = 10L * Transaction.micronyzoMultiplierRatio;

    public static List<Transaction> approvedTransactionsForBlock(List<Transaction> transactions, Block previousBlock,
                                                                 boolean forBlockAssembly) {

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
            LogUtil.println("removed transaction because timestamp was before beginning of block");
        }
        while (dedupedTransactions.size() > 0 &&
                dedupedTransactions.get(dedupedTransactions.size() - 1).getTimestamp() >= endTimestamp) {
            dedupedTransactions.remove(dedupedTransactions.size() - 1);
            LogUtil.println("removed transaction because timestamp was past end of block");
        }

        // Remove all transactions with invalid types.
        Set<Byte> validTypes;
        if (blockHeight == 0) {
            validTypes = new HashSet<>(Arrays.asList(Transaction.typeCoinGeneration, Transaction.typeSeed,
                    Transaction.typeStandard));
        } else if (previousBlock.getBlockchainVersion() == 0) {
            validTypes = new HashSet<>(Arrays.asList(Transaction.typeSeed, Transaction.typeStandard));
        } else if (previousBlock.getBlockchainVersion() == 1) {
            validTypes = new HashSet<>(Arrays.asList(Transaction.typeSeed, Transaction.typeStandard,
                    Transaction.typeCycle));
        } else {
            validTypes = new HashSet<>(Arrays.asList(Transaction.typeSeed, Transaction.typeStandard,
                    Transaction.typeCycle, Transaction.typeCycleSignature));
        }
        for (int i = dedupedTransactions.size() - 1; i >= 0; i--) {
            if (!validTypes.contains(dedupedTransactions.get(i).getType())) {
                dedupedTransactions.remove(i);
                LogUtil.println("removed transaction because type is invalid");
            }
        }

        // Remove all transactions other than cycle-signature transactions less than 1 micronyzo.
        for (int i = dedupedTransactions.size() - 1; i >= 0; i--) {
            if (dedupedTransactions.get(i).getType() != Transaction.typeCycleSignature &&
                    dedupedTransactions.get(i).getAmount() < 1L) {
                dedupedTransactions.remove(i);
                LogUtil.println("removed transaction at index " + i + " due to amount less than 1 micronyzo");
            }
        }

        // Check the previous-block hash here. Properly used, this provides protection against eclipse attacks.
        for (int i = dedupedTransactions.size() - 1; i >= 0; i--) {
            if (!dedupedTransactions.get(i).previousHashIsValid()) {
                dedupedTransactions.remove(i);
                LogUtil.println("removed transaction because previous hash was invalid");
            }
        }

        // Protect the seed-funding account from all transactions other than the transactions published on day 1.
        protectSeedFundingAccount(dedupedTransactions, blockHeight);

        // Remove any transactions with invalid signatures.
        for (int i = dedupedTransactions.size() - 1; i >= 0; i--) {
            if (!dedupedTransactions.get(i).signatureIsValid()) {
                dedupedTransactions.remove(i);
                LogUtil.println("removed transaction because signature was invalid");
            }
        }

        // Enforce the rules for cycle transactions.
        enforceCycleTransactionRules(dedupedTransactions, previousBlock.getBlockchainVersion());

        // Enforce the rules for accounts subject to the locking threshold.
        BalanceList balanceList = BalanceListManager.balanceListForBlock(previousBlock);
        enforceLockingRules(dedupedTransactions, balanceList.getBlockchainVersion(), balanceList.getUnlockThreshold(),
                balanceList.getUnlockTransferSum());

        // Assemble the final list of transactions with valid amounts. This has to be done in ascending order of
        // timestamp, because older transactions take precedence over newer transactions.
        List<Transaction> approvedTransactions = new ArrayList<>();
        Map<ByteBuffer, Long> identifierToBalanceMap = makeBalanceMap(balanceList);
        for (Transaction transaction : dedupedTransactions) {
            ByteBuffer senderIdentifier = transaction.getType() == Transaction.typeCycle ?
                    ByteBuffer.wrap(BalanceListItem.cycleAccountIdentifier) :
                    ByteBuffer.wrap(transaction.getSenderIdentifier());
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
                LogUtil.println("removed transaction because amount " + transaction.getAmount() + " was greater " +
                        "than balance " + senderBalance);
            }
        }

        // If this method is being used for new-block assembly and the list of approved transactions is larger than
        // allowed for the block, remove the smallest transactions until the list is an acceptable size.
        int maximumListSize = BlockchainMetricsManager.maximumTransactionsForBlockAssembly();
        if (approvedTransactions.size() > maximumListSize && forBlockAssembly) {

            // Sort the transactions on amount descending, promoting cycle-signature transactions to the top of the
            // list.
            approvedTransactions.sort(new Comparator<Transaction>() {
                @Override
                public int compare(Transaction transaction1, Transaction transaction2) {
                    long transaction1CompareAmount = transaction1.getType() == Transaction.typeCycleSignature ?
                            Long.MAX_VALUE : transaction1.getAmount();
                    long transaction2CompareAmount = transaction2.getType() == Transaction.typeCycleSignature ?
                            Long.MAX_VALUE : transaction2.getAmount();
                    return Long.compare(transaction2CompareAmount, transaction1CompareAmount);
                }
            });

            // Remove the tail of the list until it is an appropriate size.
            while (approvedTransactions.size() > maximumListSize) {
                approvedTransactions.remove(approvedTransactions.size() - 1);
            }

            // Sort the list back to block order.
            sortTransactions(approvedTransactions);
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

    public static List<Transaction> transactionsWithoutDuplicates(List<Transaction> transactions) {

        // This method has been modified to eliminate any potential concern of signature malleability in Ed25519. To our
        // understanding, signature malleability in Ed25519 could only be used to create signatures that would validate
        // under a different public key, which would make the technique useless for duplicating a transaction.
        // However, the extra byte-for-byte check of the transaction is computationally cheap, and it provides an extra
        // layer of assurance that duplicate transactions will be removed.

        Set<ByteBuffer> signaturesAdded = new HashSet<>();
        Set<ByteBuffer> rawBytesAdded = new HashSet<>();

        List<Transaction> transactionsWithoutDuplicates = new ArrayList<>();
        for (Transaction transaction : transactions) {

            ByteBuffer signature = ByteBuffer.wrap(transaction.getSignature());
            ByteBuffer rawBytes = ByteBuffer.wrap(transaction.getBytes(true));

            if (!signaturesAdded.contains(signature) && !rawBytesAdded.contains(rawBytes)) {
                signaturesAdded.add(signature);
                rawBytesAdded.add(rawBytes);
                transactionsWithoutDuplicates.add(transaction);
            }
        }

        return transactionsWithoutDuplicates;
    }

    public static boolean transactionSpamsBalanceList(Map<ByteBuffer, Long> balanceMap, Transaction transaction,
                                                      List<Transaction> allTransactionsInBlock) {

        // To prevent issues related to an exceptionally large balance list, some limitations are needed to avoid the
        // creation of many small accounts. There are two ways to create many accounts with little funds: directly, by
        // transferring a small amount to a new account, and indirectly, by transferring a larger amount away from an
        // account to create a new account, leaving very little in the source account. Both of these cases are addressed
        // here.

        // The default value is false, and the value will only be set to true if certain conditions are met. This
        // could actually be written in a single line, but the logic is easier to read this way.
        boolean isSpam = false;

        // Only standard transactions are of concern.
        if (transaction.getType() == Transaction.typeStandard) {

            // This is the direct case. A ∩10 transaction will produce a new account of slightly less than ∩10, but this
            // is not an issue. We are simply trying to make it difficult to spam the balance list. The exact threshold
            // is less important than having a threshold significantly more than μ1, and a minimum transaction of ∩10
            // for a new account is less confusing than a minimum of ∩10.025063. A transaction of only μ1 will not spam
            // the balance list, as the full transaction amount is consumed by the transaction fee, and a new entry is
            // not created in the balance list.
            if (!balanceMap.keySet().contains(ByteBuffer.wrap(transaction.getReceiverIdentifier())) &&
                    transaction.getAmount() > 1L && transaction.getAmount() < minimumPreferredBalance) {
                isSpam = true;
            } else {

                // This is the indirect case. The existing account needs to have at least ∩10 in it or be empty after
                // the block. All transactions must be considered, or multiple transactions could be sent from a single
                // account to bypass the rule.
                long senderBalance = balanceMap.getOrDefault(ByteBuffer.wrap(transaction.getSenderIdentifier()),
                        0L);
                long senderSum = 0L;
                for (Transaction blockTransaction : allTransactionsInBlock) {
                    if (ByteUtil.arraysAreEqual(transaction.getSenderIdentifier(),
                            blockTransaction.getSenderIdentifier())) {
                        senderSum += blockTransaction.getAmount();
                    }
                }
                if (senderBalance - senderSum < minimumPreferredBalance && senderBalance - senderSum != 0) {
                    isSpam = true;
                }
            }
        }

        return isSpam;
    }

    public static List<Transaction> transactionsWithoutBalanceListSpam(Map<ByteBuffer, Long> balanceMap,
                                                                       List<Transaction> transactions) {

        List<Transaction> transactionsFiltered = new ArrayList<>();
        for (Transaction transaction : transactions) {
            if (!transactionSpamsBalanceList(balanceMap, transaction, transactions)) {
                transactionsFiltered.add(transaction);
            }
        }

        return transactionsFiltered;
    }

    public static int numberOfTransactionsSpammingBalanceList(Map<ByteBuffer, Long> balanceMap,
                                                              List<Transaction> transactions) {

        int numberOfTransactions = 0;
        for (Transaction transaction : transactions) {
            if (transactionSpamsBalanceList(balanceMap, transaction, transactions)) {
                numberOfTransactions++;
            }
        }

        return numberOfTransactions;
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
                    LogUtil.println("removed non-seed transaction from seed-funding account");
                }

                if (transaction.getAmount() != transactionAmount) {
                    needToRemoveTransaction = true;
                    LogUtil.println("removed seed transaction with incorrect amount: " +
                            PrintUtil.printAmount(transaction.getAmount()) + ", expected " +
                            PrintUtil.printAmount(transactionAmount));
                }

                if (transaction.getTimestamp() != transactionTimestamp) {
                    needToRemoveTransaction = true;
                    LogUtil.println("removed seed transaction with incorrect timestamp: " +
                            PrintUtil.printTimestamp(transaction.getTimestamp()) + ", expected " +
                            PrintUtil.printTimestamp(transactionTimestamp));
                }

                if (transaction.getSenderData().length > 0) {
                    needToRemoveTransaction = true;
                    LogUtil.println("removed seed transaction with non-empty sender data: " +
                            ByteUtil.arrayAsStringNoDashes(transaction.getSenderData()));
                }

                if (transaction.getPreviousHashHeight() != 0L) {
                    needToRemoveTransaction = true;
                    LogUtil.println("removed seed transaction with previous-hash height of " +
                            transaction.getPreviousHashHeight());
                }

                // Perform the actual removal, if necessary.
                if (needToRemoveTransaction) {
                    dedupedTransactions.remove(i);
                }
            }
        }
    }

    private static void enforceCycleTransactionRules(List<Transaction> dedupedTransactions, int blockchainVersion) {

        // If the blockchain is earlier than version 1, remove all cycle transactions.
        if (blockchainVersion < 1) {
            for (int i = dedupedTransactions.size() - 1; i >= 0; i--) {
                if (dedupedTransactions.get(i).getType() == Transaction.typeCycle) {
                    dedupedTransactions.remove(i);
                    LogUtil.println("removed cycle transaction due to blockchain version less than 1");
                }
            }
        } else {
            // For blockchain version 1 and later, only allow cycle transactions from in-cycle verifiers.
            for (int i = dedupedTransactions.size() - 1; i >= 0; i--) {
                Transaction transaction = dedupedTransactions.get(i);
                if (transaction.getType() == Transaction.typeCycle &&
                        !BlockManager.verifierInCurrentCycle(ByteBuffer.wrap(transaction.getSenderIdentifier()))) {
                    dedupedTransactions.remove(i);
                    LogUtil.println("removed cycle transaction from out-of-cycle verifier");
                }
            }
        }

        // If the blockchain is earlier than version 2, remove all cycle-signature transactions.
        if (blockchainVersion < 2) {
            for (int i = dedupedTransactions.size() - 1; i >= 0; i--) {
                int type = dedupedTransactions.get(i).getType();
                if (type == Transaction.typeCycleSignature) {
                    dedupedTransactions.remove(i);
                    LogUtil.println("removed cycle-signature transaction due to blockchain version less than 2");
                }
            }
        } else {
            // For blockchain version 2 and later, only allow cycle-signature transactions from in-cycle verifiers.
            for (int i = dedupedTransactions.size() - 1; i >= 0; i--) {
                Transaction transaction = dedupedTransactions.get(i);
                if (transaction.getType() == Transaction.typeCycleSignature &&
                        !BlockManager.verifierInCurrentCycle(ByteBuffer.wrap(transaction.getSenderIdentifier()))) {
                    dedupedTransactions.remove(i);
                    LogUtil.println("removed cycle-signature transaction from out-of-cycle verifier");
                }
            }
        }

        // Remove any cycle transactions over ∩100,000.
        for (int i = dedupedTransactions.size() - 1; i >= 0; i--) {
            Transaction transaction = dedupedTransactions.get(i);
            if (transaction.getType() == Transaction.typeCycle &&
                    transaction.getAmount() > Transaction.maximumCycleTransactionAmount) {
                dedupedTransactions.remove(i);
                LogUtil.println("removed cycle transaction over ∩100,000: " +
                        PrintUtil.printAmount(transaction.getAmount()));
            }
        }

        // For version 1, remove any cycle transactions with insufficient signatures, duplicate signatures, out-of-cycle
        // signatures, or invalid signatures.
        if (blockchainVersion == 1) {
            for (int i = dedupedTransactions.size() - 1; i >= 0; i--) {
                Transaction transaction = dedupedTransactions.get(i);
                if (transaction.getType() == Transaction.typeCycle) {
                    // To make this calculation invulnerable to manipulations from a single verifier attempting to
                    // submit multiple signatures, we count the number of verifiers in the current cycle for which a
                    // valid signature is not present.
                    Set<ByteBuffer> currentCycle = BlockManager.verifiersInCurrentCycleSet();
                    int cycleLength = currentCycle.size();
                    int missingThreshold = cycleLength / 4;

                    // Make a new set of all verifier identifiers in the current cycle. Then, remove all identifiers for
                    // which a valid signature is found.
                    Set<ByteBuffer> signaturesMissing = new HashSet<>(currentCycle);
                    boolean transactionIsValid = true;
                    if (currentCycle.contains(ByteBuffer.wrap((transaction.getSenderIdentifier())))) {
                        signaturesMissing.remove(ByteBuffer.wrap(transaction.getSenderIdentifier()));
                        Map<ByteBuffer, byte[]> cycleSignatures = transaction.getCycleSignatures();
                        for (ByteBuffer identifier : cycleSignatures.keySet()) {
                            if (signaturesMissing.contains(identifier)) {
                                if (transaction.signatureIsValid(identifier.array(), cycleSignatures.get(identifier))) {
                                    signaturesMissing.remove(identifier);
                                } else {
                                    // A signature is invalid. This makes the entire transaction invalid.
                                    transactionIsValid = false;
                                }
                            } else {
                                // A verifier was included twice in the signature list, the initiator was included in
                                // the signature list, or an out-of-cycle verifier was included in the signature list.
                                // This makes the entire transaction invalid.
                                transactionIsValid = false;
                            }
                        }
                    } else {
                        // The initiator of the transaction is not in the cycle. This makes the transaction invalid.
                        transactionIsValid = false;
                    }

                    // If the transaction is invalid or the number of signatures missing exceeds the threshold, remove the
                    // transaction.
                    if (!transactionIsValid || signaturesMissing.size() > missingThreshold) {
                        dedupedTransactions.remove(i);
                        LogUtil.println("removed cycle transaction because " + signaturesMissing.size() +
                                " signatures were missing with a threshold of " + missingThreshold + ", cycle length=" +
                                cycleLength + ", or because transaction was invalid (valid=" + transactionIsValid + ")");
                    }
                }
            }
        } else {
            // For versions other than 1, remove all cycle transactions with bundled signatures.
            for (int i = dedupedTransactions.size() - 1; i >= 0; i--) {
                int type = dedupedTransactions.get(i).getType();
                if (type == Transaction.typeCycle && dedupedTransactions.get(i).getCycleSignatures().size() > 0) {
                    dedupedTransactions.remove(i);
                    LogUtil.println("removed cycle transaction with bundled signatures due to blockchain version not " +
                            "equal to 1");
                }
            }
        }
    }

    private static void enforceLockingRules(List<Transaction> dedupedTransactions, int blockchainVersion,
                                            long unlockThreshold, long unlockTransferSum) {

        // Only enforce the locking rules for versions 1 and greater.
        if (blockchainVersion >= 1) {

            // Determine the sum of transactions from locked accounts.
            long transactionSumFromLockedAccounts = 0L;
            for (Transaction transaction : dedupedTransactions) {
                if (LockedAccountManager.isSubjectToLock(transaction)) {
                    transactionSumFromLockedAccounts += transaction.getAmount();
                }
            }

            // If the sum is greater than the available threshold, remove all transactions subject to locking.
            long availableTransferAmount = unlockThreshold - unlockTransferSum;
            if (transactionSumFromLockedAccounts > availableTransferAmount) {
                for (int i = dedupedTransactions.size() - 1; i >= 0; i--) {
                    if (LockedAccountManager.isSubjectToLock(dedupedTransactions.get(i))) {
                        dedupedTransactions.remove(i);
                    }
                }
            }
        }
    }
}
