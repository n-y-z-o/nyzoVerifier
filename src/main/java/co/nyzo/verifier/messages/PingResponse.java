package co.nyzo.verifier.messages;

import co.nyzo.verifier.MessageObject;

import java.nio.ByteBuffer;

public class PingResponse implements MessageObject {

    private String message = "hello";

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        byte[] messageBytes = message.getBytes();
        buffer.putShort((short) messageBytes.length);
        buffer.put(messageBytes);

        return array;
    }

    @Override
    public int getByteSize() {
        return Short.BYTES + message.getBytes().length;
    }
}
