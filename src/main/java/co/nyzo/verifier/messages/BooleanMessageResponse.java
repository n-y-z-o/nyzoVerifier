package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.FileUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class BooleanMessageResponse implements MessageObject {

    private boolean success;
    private String message;

    public BooleanMessageResponse(boolean success, String message) {

        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public int getByteSize() {
        return FieldByteSize.booleanField +       // success
                FieldByteSize.string(message);    // message
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(success ? (byte) 1 : (byte) 0);
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        buffer.putShort((short) messageBytes.length);
        buffer.put(messageBytes);

        return array;
    }

    public static BooleanMessageResponse fromByteBuffer(ByteBuffer buffer) {

        BooleanMessageResponse result = null;

        try {
            boolean success = buffer.get() == 1;
            short messageByteLength = buffer.getShort();
            byte[] messageBytes = new byte[messageByteLength];
            buffer.get(messageBytes);
            String message = new String(messageBytes, StandardCharsets.UTF_8);

            result = new BooleanMessageResponse(success, message);

        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }
}
