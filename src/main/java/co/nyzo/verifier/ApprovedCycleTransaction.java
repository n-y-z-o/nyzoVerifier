package co.nyzo.verifier;

import co.nyzo.verifier.util.LogUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ApprovedCycleTransaction implements MessageObject {

    // This class provides a reduced record of approved cycle transactions to store with the balance list. This is used
    // to enforce the ∩100,000 per 10,000-block limit of NTTP-3/3. The initiator and receiver, while not necessary for
    // this, are stored because they are small and helpful for anyone reviewing the balance list.

    private byte[] initiatorIdentifier;
    private byte[] receiverIdentifier;
    private long approvalHeight;
    private long amount;

    public ApprovedCycleTransaction(byte[] initiatorIdentifier, byte[] receiverIdentifier, long approvalHeight,
                                    long amount) {
        this.initiatorIdentifier = initiatorIdentifier;
        this.receiverIdentifier = receiverIdentifier;
        this.approvalHeight = approvalHeight;
        this.amount = amount;
    }

    public long getApprovalHeight() {
        return approvalHeight;
    }

    public long getAmount() {
        return amount;
    }

    @Override
    public int getByteSize() {
        return FieldByteSize.identifier * 2 + FieldByteSize.blockHeight + FieldByteSize.transactionAmount;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(initiatorIdentifier);
        buffer.put(receiverIdentifier);
        buffer.putLong(approvalHeight);
        buffer.putLong(amount);

        return array;
    }

    public static ApprovedCycleTransaction fromByteBuffer(ByteBuffer buffer) {

        ApprovedCycleTransaction result = null;
        try {
            byte[] initiatorIdentifier = Message.getByteArray(buffer, FieldByteSize.identifier);
            byte[] receiverIdentifier = Message.getByteArray(buffer, FieldByteSize.identifier);
            long approvalHeight = buffer.getLong();
            long amount = buffer.getLong();

            result = new ApprovedCycleTransaction(initiatorIdentifier, receiverIdentifier, approvalHeight, amount);
        } catch (Exception e) {
            LogUtil.println("exception in ApprovedCycleTransaction.fromByteBuffer(): " + e.getMessage());
        }

        return result;
    }
}
