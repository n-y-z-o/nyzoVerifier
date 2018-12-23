package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;

import java.nio.ByteBuffer;

public class NodeJoinMessage implements MessageObject, PortMessage {

    private int port;
    private String nickname;

    public NodeJoinMessage() {

        this.port = MeshListener.getPort();
        this.nickname = Verifier.getNickname();
    }

    public NodeJoinMessage(int port, String nickname) {

        this.port = port;
        this.nickname = nickname;
    }

    public int getPort() {
        return port;
    }

    public String getNickname() {
        return nickname;
    }

    @Override
    public int getByteSize() {

        return FieldByteSize.port + FieldByteSize.string(nickname);
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.putInt(port);
        Message.putString(nickname, buffer);

        return array;
    }

    public static NodeJoinMessage fromByteBuffer(ByteBuffer buffer) {

        NodeJoinMessage result = null;

        try {
            int port = buffer.getInt();
            String nickname = Message.getString(buffer);

            result = new NodeJoinMessage(port, nickname);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }
}
