package co.nyzo.verifier.tests;

import co.nyzo.verifier.BalanceList;
import co.nyzo.verifier.BalanceListItem;
import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class BalanceListTest {

    public static void main(String[] args) {

        long blockHeight = 1000L;
        byte rolloverFees = 7;
        List<byte[]> previousVerifiers = new ArrayList<>();

        List<BalanceListItem> items = new ArrayList<>();

        // Make and check and empty balance list.
        BalanceList balanceList0 = new BalanceList(blockHeight, rolloverFees, previousVerifiers, items);
        checkList(balanceList0, 0, 7);

        // Make and check a balance list with some balance entries.
        items.add(new BalanceListItem(id(2), 3000));
        items.add(new BalanceListItem(id(1), 2000));
        items.add(new BalanceListItem(id(0), 1000));
        BalanceList balanceList1 = new BalanceList(blockHeight, rolloverFees, previousVerifiers, items);
        checkList(balanceList1, 3, 6007);

        // Make and check a balance list with some duplicate entries.
        items.clear();
        items.add(new BalanceListItem(id(0), 1000));
        items.add(new BalanceListItem(id(0), 1000));
        items.add(new BalanceListItem(id(1), 2000));
        items.add(new BalanceListItem(id(0), 1000));
        items.add(new BalanceListItem(id(2), 3000));
        items.add(new BalanceListItem(id(0), 1000));
        BalanceList balanceList2 = new BalanceList(blockHeight, rolloverFees, previousVerifiers, items);
        checkList(balanceList2, 3, 6007);

        // Make and check a balance list with some zero entries.
        items.add(new BalanceListItem(id(3), 0));
        items.add(new BalanceListItem(id(4), 0));
        items.add(new BalanceListItem(id(5), 0));
        BalanceList balanceList3 = new BalanceList(blockHeight, rolloverFees, previousVerifiers, items);
        checkList(balanceList3, 3, 6007);

        // The last three lists should all have the same hash.
        checkValue(ByteBuffer.wrap(balanceList1.getHash()), ByteBuffer.wrap(balanceList2.getHash()), "hash");
        checkValue(ByteBuffer.wrap(balanceList2.getHash()), ByteBuffer.wrap(balanceList3.getHash()), "hash");
    }

    private static void checkList(BalanceList balanceList, int expectedEntries, long expectedBalance) {

        System.out.println("balance list hash is " + PrintUtil.compactPrintByteArray(balanceList.getHash()));
        checkValue(balanceList.getItems().size(), expectedEntries, "entries");

        long balance = balanceList.getRolloverFees();
        for (BalanceListItem item : balanceList.getItems()) {
            balance += item.getBalance();
        }
        checkValue(balance, expectedBalance, "balance");
    }

    private static void checkValue(Object value, Object expectedValue, String label) {

        if (value.equals(expectedValue)) {
            System.out.println("expected " + label + "=" + expectedValue + ", " + label + "=" + value);
        } else {
            System.err.println("***** expected " + label + "=" + expectedValue + ", " + label + "=" + value + " *****");
        }
    }

    private static byte[] id(int value) {

        byte[] result = new byte[FieldByteSize.identifier];
        result[FieldByteSize.identifier - 1] = (byte) value;

        return result;
    }
}
