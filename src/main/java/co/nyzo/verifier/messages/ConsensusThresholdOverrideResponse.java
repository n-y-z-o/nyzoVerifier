package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;

import java.nio.ByteBuffer;

public class ConsensusThresholdOverrideResponse implements MessageObject {

    private boolean accepted;
    private String message;

    public ConsensusThresholdOverrideResponse(Message request) {

        if (request.isValid() && ByteUtil.arraysAreEqual(request.getSourceNodeIdentifier(), Verifier.getIdentifier())) {

            ConsensusThresholdOverrideRequest overrideRequest =
                    (ConsensusThresholdOverrideRequest) request.getContent();

            long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
            if (overrideRequest.getHeight() > frozenEdgeHeight) {
                accepted = true;
                if (overrideRequest.getThresholdPercent() == 0) {
                    message = "The consensus threshold override has been removed for height " +
                            overrideRequest.getHeight() + ".";
                } else {
                    message = "The consensus threshold has been set to " + overrideRequest.getThresholdPercent() +
                            "% for height " + overrideRequest.getHeight() + ".";
                }

                UnfrozenBlockManager.setThresholdOverride(overrideRequest.getHeight(),
                        overrideRequest.getThresholdPercent());
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

    private ConsensusThresholdOverrideResponse(boolean accepted, String message) {

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

    public static ConsensusThresholdOverrideResponse fromByteBuffer(ByteBuffer buffer) {

        boolean accepted = buffer.get() == 1;
        String message = Message.getString(buffer);

        return new ConsensusThresholdOverrideResponse(accepted, message);
    }

    @Override
    public String toString() {
        return "[ConsensusThresholdOverrideResponse(accepted=" + accepted + ", message=" + message + "]";
    }
}
