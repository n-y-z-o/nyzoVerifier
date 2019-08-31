package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class TransactionListResponse implements MessageObject {

    private List<Transaction> transactions;

    public TransactionListResponse(Message request) {

        // Both types of transaction list responses require self-signed requests.
        if (ByteUtil.arraysAreEqual(request.getSourceNodeIdentifier(), Verifier.getIdentifier())) {
            if (request.getType() == MessageType.TransactionPoolResponse14) {
                transactions = TransactionPool.allTransactions();
            } else if (request.getType() == MessageType.CycleTransactionListRequest_49) {
                transactions = new ArrayList<>(CycleTransactionManager.getTransactions());
            } else {
                transactions = new ArrayList<>();
            }
        } else {
            // Provide an empty result for requests that are not self-signed.
            transactions = new ArrayList<>();
        }
    }

    public TransactionListResponse(List<Transaction> transactions) {
        this.transactions = transactions;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    @Override
    public int getByteSize() {

        int size = FieldByteSize.unnamedInteger;
        for (Transaction transaction : transactions) {
            size += transaction.getByteSize();
        }

        return size;
    }

    @Override
    public byte[] getBytes() {

        int size = getByteSize();
        byte[] result = new byte[size];
        ByteBuffer buffer = ByteBuffer.wrap(result);
        buffer.putInt(transactions.size());
        for (Transaction transaction : transactions) {
            buffer.put(transaction.getBytes());
        }

        return result;
    }

    public static TransactionListResponse fromByteBuffer(ByteBuffer buffer) {

        TransactionListResponse result = null;

        try {
            List<Transaction> transactions = new ArrayList<>();

            int numberOfTransactions = buffer.getInt();
            for (int i = 0; i < numberOfTransactions; i++) {
                transactions.add(Transaction.fromByteBuffer(buffer));
            }

            result = new TransactionListResponse(transactions);
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
