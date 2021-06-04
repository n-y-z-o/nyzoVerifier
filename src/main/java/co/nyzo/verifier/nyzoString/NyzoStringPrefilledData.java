package co.nyzo.verifier.nyzoString;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.Message;
import co.nyzo.verifier.client.ClientTransactionUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class NyzoStringPrefilledData implements NyzoString {

    private byte[] receiverIdentifier;
    private byte[] senderData;
    private long amount;

    public NyzoStringPrefilledData(byte[] receiverIdentifier, byte[] senderData, long amount) {
        this.receiverIdentifier = receiverIdentifier;
        if (senderData.length <= FieldByteSize.maximumSenderDataLength) {
            this.senderData = senderData;
        } else {
            this.senderData = Arrays.copyOf(senderData, FieldByteSize.maximumSenderDataLength);
        }
        this.amount = amount;
    }

    public byte[] getReceiverIdentifier() {
        return receiverIdentifier;
    }

    public byte[] getSenderData() {
        return senderData;
    }

    public long getAmount() {
        return amount;
    }

    @Override
    public NyzoStringType getType() {
        return NyzoStringType.PrefilledData;
    }

    @Override
    public byte[] getBytes() {

        // Determine the length of the array. The single byte after the identifier encodes presence/absence of the
        // amount in its most significant bit. The 6 least-significant bits of this byte are used for the sender-data
        // length, which has a maximum value of 32.
        int length = FieldByteSize.identifier + 1 + senderData.length +
                (amount > 0 ? FieldByteSize.transactionAmount : 0);

        // Assemble the array.
        byte[] array = new byte[length];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(receiverIdentifier);
        byte combinedByte = (byte) ((amount == 0L ? 0 : 0b1000_0000) | senderData.length);
        buffer.put(combinedByte);
        buffer.put(senderData);
        if (amount > 0) {
            buffer.putLong(amount);
        }

        return array;
    }

    public static NyzoStringPrefilledData fromByteBuffer(ByteBuffer buffer) {

        // Read the receiver identifier. This is always present.
        byte[] receiverIdentifier = Message.getByteArray(buffer, FieldByteSize.identifier);

        // Read the data-length byte. The most-significant bit of this byte indicates whether an amount is present.
        byte dataLengthByte = buffer.get();
        boolean amountPresent = (dataLengthByte & 0b1000_0000) == 0b1000_0000;

        // Read the sender-data length and the sender data. The sender-data length uses the 6 least-significant bits,
        // with a maximum value of 0x0010_0000 (decimal 32).
        int senderDataLength = Math.min(dataLengthByte & 0b0011_1111, FieldByteSize.maximumSenderDataLength);
        byte[] senderData = Message.getByteArray(buffer, senderDataLength);

        // Read the amount, if present. Ensure it is not negative.
        long amount = 0L;
        if (amountPresent) {
            amount = buffer.getLong();
        }
        amount = Math.max(0L, amount);

        return new NyzoStringPrefilledData(receiverIdentifier, senderData, amount);
    }

    @Override
    public String toString() {
        return "[NyzoStringPrefilledData: receiverId=" + PrintUtil.compactPrintByteArray(getReceiverIdentifier()) +
                ", senderData=" + ClientTransactionUtil.senderDataForDisplay(getSenderData()) + ", amount=" +
                PrintUtil.printAmountWithCommas(getAmount()) + "]";
    }
}
