package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class UnfrozenBlockPoolPurgeResponse implements MessageObject {

    private boolean accepted;
    private String message;

    public UnfrozenBlockPoolPurgeResponse(Message purgeRequest) {

        try {
            StringBuilder error = new StringBuilder();
            if (isValidPurgeRequest(purgeRequest, error)) {
                accepted = true;
                message = "Purge request accepted";
                purgeUnfrozenBlockPool();
            } else {
                accepted = false;
                message = "Purge request not accepted (error=\"" + error + "\")";
            }
        } catch (Exception e) {
            accepted = false;
            message = "Purge request not accepted (error=\"" + PrintUtil.printException(e) + "\")";
        }
    }

    private UnfrozenBlockPoolPurgeResponse(boolean accepted, String message) {
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
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        buffer.putShort((short) messageBytes.length);
        buffer.put(messageBytes);

        return array;
    }

    public static UnfrozenBlockPoolPurgeResponse fromByteBuffer(ByteBuffer buffer) {

        UnfrozenBlockPoolPurgeResponse result = null;

        try {
            boolean accepted = buffer.get() == 1;
            short messageByteLength = buffer.getShort();
            byte[] messageBytes = new byte[messageByteLength];
            buffer.get(messageBytes);
            String message = new String(messageBytes, StandardCharsets.UTF_8);

            result = new UnfrozenBlockPoolPurgeResponse(accepted, message);

        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    private static boolean isValidPurgeRequest(Message purgeRequest, StringBuilder error) {

        boolean valid = true;
        if (!ByteUtil.arraysAreEqual(purgeRequest.getSourceNodeIdentifier(), Verifier.getIdentifier())) {
            error.append("The identifier, " +
                    ByteUtil.arrayAsStringWithDashes(purgeRequest.getSourceNodeIdentifier()) + ", is incorrect. ");
            valid = false;
        }

        if (!purgeRequest.isValid()) {
            error.append("The signature is invalid. ");
            valid = false;
        }

        if (error.length() > 0 && error.charAt(error.length() - 1) == ' ') {
            error.deleteCharAt(error.length() - 1);
        }

        return valid;
    }

    private static void purgeUnfrozenBlockPool() {

        UnfrozenBlockManager.purge();
    }

    @Override
    public String toString() {
        return "[UnfrozenBlockPoolPurgeResponse(" + (accepted ? "accepted" : "not accepted") + ", message=\"" +
                message + "\")]";
    }
}
