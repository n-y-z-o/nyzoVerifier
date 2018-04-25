package co.nyzo.verifier;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class BalanceList implements MessageObject {

    private long blockHeight;
    private byte rolloverFees;
    private List<byte[]> previousVerifiers;
    private List<BalanceListItem> items;

    public BalanceList(long blockHeight, byte rolloverFees, List<byte[]> previousVerifiers,
                       List<BalanceListItem> items) {

        this.blockHeight = blockHeight;
        this.rolloverFees = rolloverFees;
        this.previousVerifiers = previousVerifiers;
        this.items = normalize(items);
    }

    private static List<BalanceListItem> normalize(List<BalanceListItem> balanceItems) {

        // Sort first to make removal of duplicates easier.
        List<BalanceListItem> sorted = new ArrayList<>(balanceItems);
        Collections.sort(sorted, new Comparator<BalanceListItem>() {
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
        });

        for (int i = sorted.size() - 1; i >= 0; i--) {
            if (sorted.get(i).getBalance() == 0L) {
                sorted.remove(i);
            }
        }

        // TODO: remove any duplicates

        return sorted;
    }

    public long getBlockHeight() {
        return blockHeight;
    }

    public byte getRolloverFees() {
        return rolloverFees;
    }

    public List<byte[]> getPreviousVerifiers() {
        return new ArrayList<>(previousVerifiers);
    }

    public List<BalanceListItem> getItems() {
        return items;
    }

    public static BalanceList fromByteBuffer(ByteBuffer buffer) {

        long blockHeight = buffer.getLong();
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
            byte[] identifier = new byte[32];
            buffer.get(identifier);
            long balance = buffer.getLong();
            items.add(new BalanceListItem(identifier, balance));
        }

        return new BalanceList(blockHeight, rolloverFees, previousVerifiers, items);
    }

    @Override
    public int getByteSize() {
        int numberOfPreviousVerifiers = (int) Math.min(blockHeight, 9);

        return FieldByteSize.blockHeight +
                FieldByteSize.rolloverTransactionFees +
                FieldByteSize.identifier * numberOfPreviousVerifiers +
                FieldByteSize.balanceListLength +
                (FieldByteSize.identifier + FieldByteSize.transactionAmount) * items.size();
    }

    @Override
    public byte[] getBytes() {

        byte[] result = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(result);
        buffer.putLong(blockHeight);
        buffer.put(rolloverFees);
        for (byte[] previousVerifier : previousVerifiers) {
            buffer.put(previousVerifier);
        }
        buffer.putInt(items.size());
        for (BalanceListItem item : items) {
            buffer.put(item.getIdentifier());
            buffer.putLong(item.getBalance());
        }

        return result;
    }

    public byte[] getHash() {

        return HashUtil.doubleSHA256(getBytes());
    }
}
