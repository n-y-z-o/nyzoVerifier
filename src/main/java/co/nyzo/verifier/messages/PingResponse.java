package co.nyzo.verifier.messages;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.MessageObject;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class PingResponse implements MessageObject {

    private String message;

    public PingResponse(String message) {
        this.message = message;
    }

    @Override
    public int getByteSize() {
        return FieldByteSize.string(message);
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        byte[] messageBytes = message.getBytes();
        buffer.putShort((short) messageBytes.length);
        buffer.put(messageBytes);

        return array;
    }

    public static PingResponse fromByteBuffer(ByteBuffer buffer) {

        PingResponse result = null;

        try {
            short messageByteLength = buffer.getShort();
            byte[] messageBytes = new byte[messageByteLength];
            buffer.get(messageBytes);
            String message = new String(messageBytes, StandardCharsets.UTF_8);

            result = new PingResponse(message);

        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    @Override
    public String toString() {
        return message == null ? "null" : message;
    }
}
