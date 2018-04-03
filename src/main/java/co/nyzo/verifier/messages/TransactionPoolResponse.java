package co.nyzo.verifier.messages;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.MessageObject;
import co.nyzo.verifier.Transaction;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class TransactionPoolResponse implements MessageObject {

    private List<Transaction> transactions;

    public TransactionPoolResponse(List<Transaction> transactions) {
        this.transactions = transactions;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    @Override
    public int getByteSize() {

        int size = FieldByteSize.transactionPoolLength;
        for (Transaction transaction : transactions) {
            size += transaction.getByteSize();
        }

        return size;
    }

    @Override
    public byte[] getBytes() {

        int size = getByteSize();
        System.out.println("byte size of transaction pool response: " + size);
        System.out.println("transaction pool size: " + transactions.size());
        byte[] result = new byte[size];
        ByteBuffer buffer = ByteBuffer.wrap(result);
        buffer.putInt(transactions.size());
        for (Transaction transaction : transactions) {
            buffer.get(transaction.getBytes());
        }

        return result;
    }

    public static TransactionPoolResponse fromByteBuffer(ByteBuffer buffer) {

        TransactionPoolResponse result = null;

        try {
            List<Transaction> transactions = new ArrayList<>();

            int numberOfTransactions = buffer.getInt();
            for (int i = 0; i < numberOfTransactions; i++) {
                transactions.add(Transaction.fromByteBuffer(buffer));
            }

            result = new TransactionPoolResponse(transactions);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("[TransactionPoolResponse(" + transactions.size() + "): {");
        String separator = "";
        for (int i = 0; i < transactions.size() && i < 5; i++) {
            result.append(separator + transactions.get(i).toString());
            separator = ",";
        }
        if (transactions.size() > 5) {
            result.append("...");
        }
        result.append("}]");

        return result.toString();
    }
}
