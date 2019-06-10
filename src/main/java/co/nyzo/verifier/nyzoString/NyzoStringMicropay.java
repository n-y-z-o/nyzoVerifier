package co.nyzo.verifier.nyzoString;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.Message;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class NyzoStringMicropay implements NyzoString {

    private byte[] receiverIdentifier;
    private byte[] senderData;
    private long amount;
    private byte[] receiverIpAddress;
    private int receiverPort;

    public NyzoStringMicropay(byte[] receiverIdentifier, byte[] senderData, long amount, byte[] receiverIpAddress,
                              int receiverPort) {
        this.receiverIdentifier = receiverIdentifier;
        if (senderData.length <= FieldByteSize.maximumSenderDataLength) {
            this.senderData = senderData;
        } else {
            this.senderData = Arrays.copyOf(senderData, FieldByteSize.maximumSenderDataLength);
        }
        this.amount = amount;
        this.receiverIpAddress = receiverIpAddress;
        this.receiverPort = receiverPort;
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

    public byte[] getReceiverIpAddress() {
        return receiverIpAddress;
    }

    public int getReceiverPort() {
        return receiverPort;
    }

    @Override
    public NyzoStringType getType() {
        return NyzoStringType.Micropay;
    }

    @Override
    public byte[] getBytes() {

        int length = FieldByteSize.identifier + 1 + senderData.length + FieldByteSize.transactionAmount +
                FieldByteSize.ipAddress + FieldByteSize.port;

        byte[] array = new byte[length];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(receiverIdentifier);
        buffer.put((byte) senderData.length);
        buffer.put(senderData);
        buffer.putLong(amount);
        buffer.put(receiverIpAddress);
        buffer.putInt(receiverPort);

        return array;
    }

    public static NyzoStringMicropay fromByteBuffer(ByteBuffer buffer) {

        byte[] receiverIdentifier = Message.getByteArray(buffer, FieldByteSize.identifier);
        int senderDataLength = Math.min(buffer.get() & 0xff, FieldByteSize.maximumSenderDataLength);
        byte[] senderData = Message.getByteArray(buffer, senderDataLength);
        long amount = buffer.getLong();
        byte[] receiverIpAddress = Message.getByteArray(buffer, FieldByteSize.ipAddress);
        int receiverPort = buffer.getInt();

        return new NyzoStringMicropay(receiverIdentifier, senderData, amount, receiverIpAddress, receiverPort);
    }
}
