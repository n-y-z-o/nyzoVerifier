package co.nyzo.verifier;

import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BalanceList implements MessageObject {

    private static final Comparator<BalanceListItem> balanceListItemComparator = new Comparator<BalanceListItem>() {
        @Override
        public int compare(BalanceListItem pair1, BalanceListItem pair2) {
            int result = 0;
            byte[] identifier1 = pair1.getIdentifier();
            byte[] identifier2 = pair2.getIdentifier();
            for (int i = 0; i < FieldByteSize.identifier && result == 0; i++) {
                int byte1 = identifier1[i] & 0xff;
                int byte2 = identifier2[i] & 0xff;
                if (byte1 < byte2) {
                    result = -1;
                } else if (byte2 < byte1) {
                    result = 1;
                }
            }

            return result;
        }
    };

    private int blockchainVersion;
    private long blockHeight;
    private byte rolloverFees;
    private List<byte[]> previousVerifiers;
    private List<BalanceListItem> items;
    private long unlockThreshold;
    private long unlockTransferSum;
    private Map<ByteBuffer, Transaction> pendingCycleTransactions;
    private List<ApprovedCycleTransaction> recentlyApprovedCycleTransactions;

    public BalanceList(int blockchainVersion, long blockHeight, byte rolloverFees, List<byte[]> previousVerifiers,
                       List<BalanceListItem> items, long unlockThreshold, long unlockTransferSum,
                       Map<ByteBuffer, Transaction> pendingCycleTransactions,
                       List<ApprovedCycleTransaction> recentlyApprovedCycleTransactions) {

        this.blockchainVersion = Block.limitBlockchainVersion(blockchainVersion);
        this.blockHeight = blockHeight;
        this.rolloverFees = rolloverFees;
        this.previousVerifiers = previousVerifiers;
        this.items = normalize(items);
        this.unlockThreshold = this.blockchainVersion == 0 ? 0 : unlockThreshold;      // implicitly 0 for version 0
        this.unlockTransferSum = this.blockchainVersion == 0 ? 0 : unlockTransferSum;  // implicitly 0 for version 0
        this.pendingCycleTransactions = this.blockchainVersion < 2 ? new ConcurrentHashMap<>() :
                pendingCycleTransactions;
        this.recentlyApprovedCycleTransactions = this.blockchainVersion < 2 ? new ArrayList<>() :
                recentlyApprovedCycleTransactions;
    }

    private static List<BalanceListItem> normalize(List<BalanceListItem> balanceItems) {

        // Sort first to make removal of duplicates easier.
        List<BalanceListItem> sorted = new ArrayList<>(balanceItems);
        Collections.sort(sorted, balanceListItemComparator);

        // Remove any entries with balances of zero or less. This is actually a protection against overdrafts, because
        // it will ensure that the signature does not match to a balance list that contains overdrafts.
        for (int i = sorted.size() - 1; i >= 0; i--) {
            if (sorted.get(i).getBalance() <= 0L) {
                sorted.remove(i);
            }
        }

        // Remove any duplicate identifiers.
        for (int i = sorted.size() - 1; i >= 1; i--) {
            if (ByteUtil.arraysAreEqual(sorted.get(i).getIdentifier(), sorted.get(i - 1).getIdentifier())) {
                sorted.remove(i);
            }
        }

        return sorted;
    }

    public int getBlockchainVersion() {
        return blockchainVersion;
    }

    public long getBlockHeight() {
        return blockHeight;
    }

    public byte getRolloverFees() {
        return rolloverFees;
    }

    public List<byte[]> getPreviousVerifiers() {
        return previousVerifiers;
    }

    public List<BalanceListItem> getItems() {
        return items;
    }

    public long getUnlockThreshold() {
        return unlockThreshold;
    }

    public long getUnlockTransferSum() {
        return unlockTransferSum;
    }

    public Map<ByteBuffer, Transaction> getPendingCycleTransactions() {
        return pendingCycleTransactions;
    }

    public List<ApprovedCycleTransaction> getRecentlyApprovedCycleTransactions() {
        return recentlyApprovedCycleTransactions;
    }

    public static BalanceList fromByteBuffer(ByteBuffer buffer) {

        ShortLong versionAndHeight = ShortLong.fromByteBuffer(buffer);
        int blockchainVersion = versionAndHeight.getShortValue();
        long blockHeight = versionAndHeight.getLongValue();
        byte rolloverFees = buffer.get();

        int numberOfPreviousVerifiers = (int) Math.min(blockHeight, 9);
        List<byte[]> previousVerifiers = new ArrayList<>();
        for (int i = 0; i < numberOfPreviousVerifiers; i++) {
            byte[] verifierIdentifier = new byte[FieldByteSize.identifier];
            buffer.get(verifierIdentifier);
            previousVerifiers.add(verifierIdentifier);
        }

        long numberOfPairs = buffer.getInt();
        List<BalanceListItem> items = new ArrayList<>();
        for (int i = 0; i < numberOfPairs; i++) {
            byte[] identifier = new byte[FieldByteSize.identifier];
            buffer.get(identifier);
            long balance = buffer.getLong();
            short blocksUntilFee = buffer.getShort();
            items.add(new BalanceListItem(identifier, balance, blocksUntilFee));
        }

        long unlockThreshold = 0L;
        long unlockTransferSum = 0L;
        if (blockchainVersion > 0) {
            unlockThreshold = buffer.getLong();
            unlockTransferSum = buffer.getLong();
        }

        Map<ByteBuffer, Transaction> pendingCycleTransactions = new ConcurrentHashMap<>();
        if (blockchainVersion > 1) {
            int numberOfTransactions = buffer.getInt();
            for (int i = 0; i < numberOfTransactions; i++) {
                Transaction transaction = Transaction.fromByteBuffer(buffer, 0, new byte[FieldByteSize.hash], true);
                pendingCycleTransactions.put(ByteBuffer.wrap(transaction.getSenderIdentifier()), transaction);
            }
        }

        List<ApprovedCycleTransaction> recentlyApprovedCycleTransactions = new ArrayList<>();
        if (blockchainVersion > 1) {
            int numberOfTransactions = buffer.getInt();
            for (int i = 0; i < numberOfTransactions; i++) {
                recentlyApprovedCycleTransactions.add(ApprovedCycleTransaction.fromByteBuffer(buffer));
            }
        }

        return new BalanceList(blockchainVersion, blockHeight, rolloverFees, previousVerifiers, items, unlockThreshold,
                unlockTransferSum, pendingCycleTransactions, recentlyApprovedCycleTransactions);
    }

    @Override
    public int getByteSize() {
        int numberOfPreviousVerifiers = (int) Math.min(blockHeight, 9);
        int bytesPerItem = FieldByteSize.identifier + FieldByteSize.transactionAmount + FieldByteSize.blocksUntilFee;

        int size = FieldByteSize.blockHeight +
                FieldByteSize.rolloverTransactionFees +
                FieldByteSize.identifier * numberOfPreviousVerifiers +
                FieldByteSize.balanceListLength +
                bytesPerItem * items.size() +
                (blockchainVersion > 0 ? FieldByteSize.transactionAmount * 2 : 0);

        // For blockchain version 2 and above, add the pending cycle transactions and approved cycle transactions.
        if (blockchainVersion > 1) {
            size += FieldByteSize.unnamedInteger;
            for (Transaction transaction : pendingCycleTransactions.values()) {
                size += transaction.getByteSize();
            }

            size += FieldByteSize.unnamedInteger;
            for (ApprovedCycleTransaction transaction : recentlyApprovedCycleTransactions) {
                size += transaction.getByteSize();
            }
        }

        return size;
    }

    @Override
    public byte[] getBytes() {

        byte[] result = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(result);
        buffer.putLong(ShortLong.combinedValue(blockchainVersion, blockHeight));
        buffer.put(rolloverFees);
        for (byte[] previousVerifier : previousVerifiers) {
            buffer.put(previousVerifier);
        }
        buffer.putInt(items.size());
        for (BalanceListItem item : items) {
            buffer.put(item.getIdentifier());
            buffer.putLong(item.getBalance());
            buffer.putShort(item.getBlocksUntilFee());
        }
        if (blockchainVersion > 0) {
            buffer.putLong(unlockThreshold);
            buffer.putLong(unlockTransferSum);
        }
        if (blockchainVersion > 1) {
            // Add the pending cycle transactions, sorted on identifier.
            List<ByteBuffer> cycleTransactionIdentifiers = new ArrayList<>(pendingCycleTransactions.keySet());
            cycleTransactionIdentifiers.sort(Transaction.identifierComparator);
            buffer.putInt(pendingCycleTransactions.size());
            for (ByteBuffer cycleTransactionIdentifier : cycleTransactionIdentifiers) {
                Transaction transaction = pendingCycleTransactions.get(cycleTransactionIdentifier);
                buffer.put(transaction.getBytes());
            }

            // Add the recently approved cycle transactions. These are naturally ordered on block height.
            buffer.putInt(recentlyApprovedCycleTransactions.size());
            for (ApprovedCycleTransaction transaction : recentlyApprovedCycleTransactions) {
                buffer.put(transaction.getBytes());
            }
        }

        return result;
    }

    public byte[] getHash() {

        return HashUtil.doubleSHA256(getBytes());
    }

    public long balanceForIdentifier(byte[] identifier) {

        // This method performs a binary search on the identifier to efficiently find the balance.
        long balance = 0L;
        int lowIndex = 0;
        int highIndex = items.size() - 1;
        BalanceListItem identifierItem = new BalanceListItem(identifier, 0L);
        while (lowIndex < highIndex && balance == 0L) {
            int midIndex = (lowIndex + highIndex) / 2;
            if (ByteUtil.arraysAreEqual(identifier, items.get(lowIndex).getIdentifier())) {
                balance = items.get(lowIndex).getBalance();
            } else if (ByteUtil.arraysAreEqual(identifier, items.get(highIndex).getIdentifier())) {
                balance = items.get(highIndex).getBalance();
            } else {
                int midComparison = balanceListItemComparator.compare(identifierItem, items.get(midIndex));
                if (midComparison > 0) {
                    lowIndex = midIndex + 1;
                } else if (midComparison < 0) {
                    highIndex = midIndex - 1;
                } else {
                    balance = items.get(midIndex).getBalance();
                }
            }
        }

        return balance;
    }

    @Override
    public String toString() {
        return "[BalanceList: height=" + getBlockHeight() + ", hash=" +
                PrintUtil.compactPrintByteArray(getHash()) + "]";
    }
}
