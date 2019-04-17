package co.nyzo.verifier.messages;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.MessageObject;

import java.nio.ByteBuffer;

public class BootstrapRequest implements MessageObject {

    private int port;

    public BootstrapRequest() {
        this.port = -1;
    }

    private BootstrapRequest(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }

    @Override
    public int getByteSize() {
        return FieldByteSize.port;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.putInt(port);

        return array;
    }

    public static BootstrapRequest fromByteBuffer(ByteBuffer buffer) {

        BootstrapRequest result = null;

        try {
            // The port is no longer used. It is stored to ensure signature integrity.
            int port = buffer.getInt();

            result = new BootstrapRequest(port);
        } catch (Exception ignored) { }

        return result;
    }
}
