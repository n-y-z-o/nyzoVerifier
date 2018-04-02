package co.nyzo.verifier;

public class BalanceListItem {

    private byte[] identifier;
    private long balance;

    public BalanceListItem(byte[] identifier, long balance) {
        this.identifier = identifier;
        this.balance = balance;
    }

    public byte[] getIdentifier() {
        return identifier;
    }

    public long getBalance() {
        return balance;
    }
}
