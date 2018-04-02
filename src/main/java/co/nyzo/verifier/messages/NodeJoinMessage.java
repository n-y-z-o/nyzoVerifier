package co.nyzo.verifier.messages;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.MessageObject;
import co.nyzo.verifier.Node;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class NodeJoinMessage implements MessageObject {

    private int port;
    private boolean fullNode;

    public NodeJoinMessage(int port, boolean fullNode) {

        this.port = port;
        this.fullNode = fullNode;
    }

    public int getPort() {
        return port;
    }

    public boolean isFullNode() {
        return fullNode;
    }

    @Override
    public int getByteSize() {
        return FieldByteSize.port + FieldByteSize.booleanField;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.putInt(port);
        buffer.put(fullNode ? (byte) 1 : (byte) 0);

        return array;
    }

    public static NodeJoinMessage fromByteBuffer(ByteBuffer buffer) {

        NodeJoinMessage result = null;

        try {
            int port = buffer.getInt();
            boolean fullNode = buffer.get() == 1;

            result = new NodeJoinMessage(port, fullNode);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }
}
