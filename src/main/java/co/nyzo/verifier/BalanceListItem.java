package co.nyzo.verifier;

import co.nyzo.verifier.util.PrintUtil;

public class BalanceListItem {

    public static final byte[] transferIdentifier = ByteUtil.byteArrayFromHexString("0000000000000000-" +
            "0000000000000000-0000000000000000-0000000000000001", FieldByteSize.identifier);

    private static final short blocksBetweenFee = 500;

    private byte[] identifier;
    private long balance;
    private short blocksUntilFee;

    public BalanceListItem(byte[] identifier, long balance) {
        this.identifier = identifier;
        this.balance = balance;
        this.blocksUntilFee = ByteUtil.arraysAreEqual(identifier, transferIdentifier) ? 0 : blocksBetweenFee;
    }

    public BalanceListItem(byte[] identifier, long balance, short blocksUntilFee) {
        this.identifier = identifier;
        this.balance = balance;
        this.blocksUntilFee = blocksUntilFee;
    }

    public byte[] getIdentifier() {
        return identifier;
    }

    public long getBalance() {
        return balance;
    }

    public short getBlocksUntilFee() {

        return blocksUntilFee;
    }

    public BalanceListItem resetBlocksUntilFee() {

        return new BalanceListItem(identifier, balance);
    }

    public BalanceListItem adjustByAmount(long amount) {

        return new BalanceListItem(identifier, balance + amount, blocksUntilFee);
    }

    public BalanceListItem decrementBlocksUntilFee() {

        return new BalanceListItem(identifier, balance, (short) Math.max(0, blocksUntilFee - 1));
    }

    @Override
    public String toString() {
        return "[BalanceListItem(id=" + PrintUtil.compactPrintByteArray(identifier) + ",balance=" +
                PrintUtil.printAmount(balance) + ",blocksUntilFee=" + blocksUntilFee + ")]";
    }
}
