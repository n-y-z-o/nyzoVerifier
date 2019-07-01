package co.nyzo.verifier.nyzoString;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.Message;
import co.nyzo.verifier.Transaction;
import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.util.PreferencesUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class NyzoStringMicropay implements NyzoString {

    private byte[] receiverIdentifier;
    private byte[] senderData;
    private long amount;
    private long timestamp;
    private long previousHashHeight;
    private byte[] previousBlockHash;

    public NyzoStringMicropay(byte[] receiverIdentifier, byte[] senderData, long amount, long timestamp,
                              long previousHashHeight, byte[] previousBlockHash) {
        this.receiverIdentifier = receiverIdentifier;
        if (senderData.length <= FieldByteSize.maximumSenderDataLength) {
            this.senderData = senderData;
        } else {
            this.senderData = Arrays.copyOf(senderData, FieldByteSize.maximumSenderDataLength);
        }
        this.amount = amount;
        this.timestamp = timestamp;
        this.previousHashHeight = previousHashHeight;
        this.previousBlockHash = previousBlockHash;
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

    public long getTimestamp() {
        return timestamp;
    }

    public long getPreviousHashHeight() {
        return previousHashHeight;
    }

    public byte[] getPreviousBlockHash() {
        return previousBlockHash;
    }

    @Override
    public NyzoStringType getType() {
        return NyzoStringType.Micropay;
    }

    @Override
    public byte[] getBytes() {

        int length = FieldByteSize.identifier + 1 + senderData.length + FieldByteSize.transactionAmount +
                FieldByteSize.timestamp + FieldByteSize.blockHeight + FieldByteSize.hash;

        byte[] array = new byte[length];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(receiverIdentifier);
        buffer.put((byte) senderData.length);
        buffer.put(senderData);
        buffer.putLong(amount);
        buffer.putLong(timestamp);
        buffer.putLong(previousHashHeight);
        buffer.put(previousBlockHash);

        return array;
    }

    public static NyzoStringMicropay fromByteBuffer(ByteBuffer buffer) {

        byte[] receiverIdentifier = Message.getByteArray(buffer, FieldByteSize.identifier);
        int senderDataLength = Math.min(buffer.get() & 0xff, FieldByteSize.maximumSenderDataLength);
        byte[] senderData = Message.getByteArray(buffer, senderDataLength);
        long amount = buffer.getLong();
        long timestamp = buffer.getLong();
        long previousHashHeight = buffer.getLong();
        byte[] previousBlockHash = Message.getByteArray(buffer, FieldByteSize.hash);

        return new NyzoStringMicropay(receiverIdentifier, senderData, amount, timestamp, previousHashHeight,
                previousBlockHash);
    }
}
