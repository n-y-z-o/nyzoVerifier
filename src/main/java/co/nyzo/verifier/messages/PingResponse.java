package co.nyzo.verifier.messages;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.Message;
import co.nyzo.verifier.MessageObject;

import java.nio.ByteBuffer;

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
        Message.putString(message, buffer);

        return array;
    }

    public static PingResponse fromByteBuffer(ByteBuffer buffer) {

        PingResponse result = null;

        try {
            String message = Message.getString(buffer);
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
