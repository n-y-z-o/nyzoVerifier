package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;

import java.nio.ByteBuffer;

public class NewVerifierVoteOverrideResponse implements MessageObject {

    private final boolean accepted;
    private final String message;

    public NewVerifierVoteOverrideResponse(Message request) {

        if (request.isValid() && ByteUtil.arraysAreEqual(request.getSourceNodeIdentifier(), Verifier.getIdentifier())) {

            NewVerifierVoteOverrideRequest overrideRequest = (NewVerifierVoteOverrideRequest) request.getContent();

            accepted = true;
            if (ByteUtil.isAllZeros(overrideRequest.getIdentifier())) {
                message = "The new-verifier vote override has been removed.";
            } else {
                message = "The new-verifier vote override has been set to " +
                        ByteUtil.arrayAsStringWithDashes(overrideRequest.getIdentifier()) + ".";
            }

            NewVerifierVoteManager.setOverride(overrideRequest.getIdentifier());

        } else {
            accepted = false;
            message = "*** unauthorized ***";
        }
    }

    private NewVerifierVoteOverrideResponse(boolean accepted, String message) {

        this.accepted = accepted;
        this.message = message;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public int getByteSize() {

        return FieldByteSize.booleanField +       // accepted
                FieldByteSize.string(message);    // message
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(accepted ? (byte) 1 : (byte) 0);
        Message.putString(message, buffer);

        return array;
    }

    public static NewVerifierVoteOverrideResponse fromByteBuffer(ByteBuffer buffer) {

        boolean accepted = buffer.get() == 1;
        String message = Message.getString(buffer);

        return new NewVerifierVoteOverrideResponse(accepted, message);
    }

    @Override
    public String toString() {
        return "[NewVerifierVoteOverrideResponse(accepted=" + accepted + ", message=" + message + "]";
    }
}
