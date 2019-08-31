package co.nyzo.verifier.messages;

import co.nyzo.verifier.CycleTransactionManager;
import co.nyzo.verifier.Message;
import co.nyzo.verifier.MessageObject;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;

public class CycleTransactionSignatureResponse implements MessageObject {

    private boolean accepted;

    public CycleTransactionSignatureResponse(boolean accepted) {
        this.accepted = accepted;
    }

    public CycleTransactionSignatureResponse(Message message) {
        if (message.getContent() instanceof CycleTransactionSignature) {
            accepted = CycleTransactionManager.registerSignature((CycleTransactionSignature) message.getContent());
        }
    }

    @Override
    public int getByteSize() {
        return 1;
    }

    @Override
    public byte[] getBytes() {
        return new byte[] { accepted ? (byte) 1 : (byte) 0 };
    }

    public static CycleTransactionSignatureResponse fromByteBuffer(ByteBuffer buffer) {

        CycleTransactionSignatureResponse result = null;
        try {
            boolean accepted = buffer.get() == 1;
            result = new CycleTransactionSignatureResponse(accepted);
        } catch (Exception ignored) { }

        return result;
    }

    @Override
    public String toString() {
        return "[CycleTransactionSignatureResponse:accepted=" + accepted + "]";
    }
}
