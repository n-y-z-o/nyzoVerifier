package co.nyzo.verifier.messages;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.Message;
import co.nyzo.verifier.MessageObject;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;

public class CycleTransactionSignature implements MessageObject {

    private byte[] transactionInitiator;
    private byte[] identifier;
    private byte[] signature;

    public CycleTransactionSignature(byte[] transactionInitiator, byte[] identifier, byte[] signature) {
        this.transactionInitiator = transactionInitiator;
        this.identifier = identifier;
        this.signature = signature;
    }

    public byte[] getTransactionInitiator() {
        return transactionInitiator;
    }

    public byte[] getIdentifier() {
        return identifier;
    }

    public byte[] getSignature() {
        return signature;
    }

    @Override
    public int getByteSize() {
        return 2 * FieldByteSize.identifier + FieldByteSize.signature;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(transactionInitiator);
        buffer.put(identifier);
        buffer.put(signature);

        return array;
    }

    public static CycleTransactionSignature fromByteBuffer(ByteBuffer buffer) {

        CycleTransactionSignature result = null;
        try {
            byte[] transactionInitiator = Message.getByteArray(buffer, FieldByteSize.identifier);
            byte[] identifier = Message.getByteArray(buffer, FieldByteSize.identifier);
            byte[] signature = Message.getByteArray(buffer, FieldByteSize.signature);

            result = new CycleTransactionSignature(transactionInitiator, identifier, signature);
        } catch (Exception ignored) { }

        return result;
    }

    @Override
    public String toString() {
        return "[CycleTransactionSignature(initiator=" + PrintUtil.superCompactPrintByteArray(transactionInitiator) +
                ",identifier=" + PrintUtil.superCompactPrintByteArray(identifier) +
                ",signature=" + PrintUtil.superCompactPrintByteArray(signature) + "]";
    }
}
