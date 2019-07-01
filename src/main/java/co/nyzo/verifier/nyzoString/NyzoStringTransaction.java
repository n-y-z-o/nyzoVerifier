package co.nyzo.verifier.nyzoString;

import co.nyzo.verifier.Transaction;

import java.nio.ByteBuffer;

public class NyzoStringTransaction implements NyzoString {

    private Transaction transaction;

    public NyzoStringTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    @Override
    public NyzoStringType getType() {
        return NyzoStringType.Transaction;
    }

    @Override
    public byte[] getBytes() {
        return transaction.getBytes(false);
    }

    public static NyzoStringTransaction fromByteBuffer(ByteBuffer buffer) {
        Transaction transaction = Transaction.fromByteBuffer(buffer);
        return new NyzoStringTransaction(transaction);
    }
}
