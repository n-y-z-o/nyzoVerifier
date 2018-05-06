package co.nyzo.verifier.messages;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.MeshListener;
import co.nyzo.verifier.MessageObject;
import co.nyzo.verifier.Node;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class NodeJoinMessage implements MessageObject {

    private int port;

    public NodeJoinMessage() {

        this.port = MeshListener.getPort();
    }

    public NodeJoinMessage(int port) {

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

    public static NodeJoinMessage fromByteBuffer(ByteBuffer buffer) {

        NodeJoinMessage result = null;

        try {
            int port = buffer.getInt();

            result = new NodeJoinMessage(port);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }
}
