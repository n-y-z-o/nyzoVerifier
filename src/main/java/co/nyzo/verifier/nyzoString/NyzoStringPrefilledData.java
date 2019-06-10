package co.nyzo.verifier.nyzoString;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.Message;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class NyzoStringPrefilledData implements NyzoString {

    private byte[] receiverIdentifier;
    private byte[] senderData;

    public NyzoStringPrefilledData(byte[] receiverIdentifier, byte[] senderData) {
        this.receiverIdentifier = receiverIdentifier;
        if (senderData.length <= FieldByteSize.maximumSenderDataLength) {
            this.senderData = senderData;
        } else {
            this.senderData = Arrays.copyOf(senderData, FieldByteSize.maximumSenderDataLength);
        }
    }

    public byte[] getReceiverIdentifier() {
        return receiverIdentifier;
    }

    public byte[] getSenderData() {
        return senderData;
    }

    @Override
    public NyzoStringType getType() {
        return NyzoStringType.PrefilledData;
    }

    @Override
    public byte[] getBytes() {

        int length = FieldByteSize.identifier + 1 + senderData.length;

        byte[] array = new byte[length];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(receiverIdentifier);
        buffer.put((byte) senderData.length);
        buffer.put(senderData);

        return array;
    }

    public static NyzoStringPrefilledData fromByteBuffer(ByteBuffer buffer) {

        byte[] receiverIdentifier = Message.getByteArray(buffer, FieldByteSize.identifier);
        int senderDataLength = Math.min(buffer.get() & 0xff, FieldByteSize.maximumSenderDataLength);
        byte[] senderData = Message.getByteArray(buffer, senderDataLength);

        return new NyzoStringPrefilledData(receiverIdentifier, senderData);
    }
}
