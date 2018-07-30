package co.nyzo.verifier;

public class BalanceListItem {

    private static final short blocksBetweenFee = 100;  // TODO: change to 17280

    private byte[] identifier;
    private long balance;
    private short blocksUntilFee;

    public BalanceListItem(byte[] identifier, long balance) {
        this.identifier = identifier;
        this.balance = balance;
        this.blocksUntilFee = blocksBetweenFee;
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
}
