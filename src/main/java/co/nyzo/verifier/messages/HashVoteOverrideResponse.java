package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;

import java.nio.ByteBuffer;

public class HashVoteOverrideResponse implements MessageObject {

    private boolean accepted;
    private String message;

    public HashVoteOverrideResponse(Message request) {

        if (request.isValid() && ByteUtil.arraysAreEqual(request.getSourceNodeIdentifier(), Verifier.getIdentifier())) {

            HashVoteOverrideRequest overrideRequest = (HashVoteOverrideRequest) request.getContent();

            long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
            if (overrideRequest.getHeight() > frozenEdgeHeight) {

                accepted = true;
                if (ByteUtil.isAllZeros(overrideRequest.getHash())) {
                    message = "The hash override has been removed for height " + overrideRequest.getHeight() + ".";
                } else {
                    message = "The hash has been set to " +
                            ByteUtil.arrayAsStringWithDashes(overrideRequest.getHash()) + " for height " +
                            overrideRequest.getHeight() + ".";
                }

                UnfrozenBlockManager.setHashOverride(overrideRequest.getHeight(), overrideRequest.getHash());
            } else {
                accepted = false;
                message = "The provided height, " + overrideRequest.getHeight() + ", is behind the frozen edge, " +
                        frozenEdgeHeight + ".";
            }

        } else {
            accepted = false;
            message = "*** unauthorized ***";
        }
    }

    private HashVoteOverrideResponse(boolean accepted, String message) {

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

    public static HashVoteOverrideResponse fromByteBuffer(ByteBuffer buffer) {

        boolean accepted = buffer.get() == 1;
        String message = Message.getString(buffer);

        return new HashVoteOverrideResponse(accepted, message);
    }

    @Override
    public String toString() {
        return "[HashVoteOverrideResponse(accepted=" + accepted + ", message=" + message + "]";
    }
}
