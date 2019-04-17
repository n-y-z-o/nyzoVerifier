package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;

import java.nio.ByteBuffer;

public class NodeJoinResponseV2 implements MessageObject, PortMessageV2 {

    private String nickname;
    private int portTcp;
    private int portUdp;

    public NodeJoinResponseV2() {

        this.nickname = Verifier.getNickname();
        this.portTcp = MeshListener.getPortTcp();
        this.portUdp = MeshListener.getPortUdp();
    }

    public NodeJoinResponseV2(String nickname, int portTcp, int portUdp) {

        this.nickname = nickname == null ? "" : nickname;
        this.portTcp = portTcp;
        this.portUdp = portUdp;
    }

    public String getNickname() {

        return nickname;
    }

    @Override
    public int getPortTcp() {
        return portTcp;
    }

    @Override
    public int getPortUdp() {
        return portUdp;
    }

    @Override
    public int getByteSize() {
        return FieldByteSize.string(nickname, NodeJoinMessageV2.maximumNicknameLengthBytes) + FieldByteSize.port * 2;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);

        Message.putString(nickname, buffer, NodeJoinMessageV2.maximumNicknameLengthBytes);
        buffer.putInt(portTcp);
        buffer.putInt(portUdp);

        return array;
    }

    public static NodeJoinResponseV2 fromByteBuffer(ByteBuffer buffer) {

        NodeJoinResponseV2 result = null;

        try {
            String nickname = Message.getString(buffer, NodeJoinMessageV2.maximumNicknameLengthBytes);
            int portTcp = buffer.getInt();
            int portUdp = buffer.getInt();

            result = new NodeJoinResponseV2(nickname, portTcp, portUdp);
        } catch (Exception ignored) { }

        return result;
    }
}
