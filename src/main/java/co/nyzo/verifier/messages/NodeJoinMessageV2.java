package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;

import java.nio.ByteBuffer;

public class NodeJoinMessageV2 implements MessageObject, PortMessageV2 {

    public static final int maximumNicknameLengthBytes = 50;

    private int portTcp;
    private int portUdp;
    private String nickname;

    public NodeJoinMessageV2() {

        this.portTcp = MeshListener.getPortTcp();
        this.portUdp = MeshListener.getPortUdp();
        this.nickname = Verifier.getNickname();
    }

    public NodeJoinMessageV2(int portTcp, int portUdp, String nickname) {

        this.portTcp = portTcp;
        this.portUdp = portUdp;
        this.nickname = nickname;
    }

    public int getPortTcp() {
        return portTcp;
    }

    @Override
    public int getPortUdp() {
        return portUdp;
    }

    public String getNickname() {
        return nickname;
    }

    @Override
    public int getByteSize() {

        return FieldByteSize.port * 2 + FieldByteSize.string(nickname, maximumNicknameLengthBytes);
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.putInt(portTcp);
        buffer.putInt(portUdp);
        Message.putString(nickname, buffer, maximumNicknameLengthBytes);

        return array;
    }

    public static NodeJoinMessageV2 fromByteBuffer(ByteBuffer buffer) {

        NodeJoinMessageV2 result = null;

        try {
            int portTcp = buffer.getInt();
            int portUdp = buffer.getInt();
            String nickname = Message.getString(buffer, maximumNicknameLengthBytes);

            result = new NodeJoinMessageV2(portTcp, portUdp, nickname);
        } catch (Exception ignored) { }

        return result;
    }
}
