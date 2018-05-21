package co.nyzo.verifier.messages;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.MeshListener;
import co.nyzo.verifier.MessageObject;
import co.nyzo.verifier.Verifier;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class NodeJoinResponse implements MessageObject {

    private String nickname;

    public NodeJoinResponse() {

        this.nickname = Verifier.getNickname();
    }

    public NodeJoinResponse(String nickname) {

        this.nickname = nickname == null ? "" : nickname;
    }

    public String getNickname() {
        return nickname;
    }

    @Override
    public int getByteSize() {
        return FieldByteSize.string(nickname);
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        byte[] nicknameBytes = nickname.getBytes(StandardCharsets.UTF_8);
        buffer.putShort((short) nicknameBytes.length);
        buffer.put(nicknameBytes);

        return array;
    }

    public static NodeJoinResponse fromByteBuffer(ByteBuffer buffer) {

        NodeJoinResponse result = null;

        try {
            short nicknameByteLength = buffer.getShort();
            byte[] nicknameBytes = new byte[nicknameByteLength];
            buffer.get(nicknameBytes);
            String nickname = new String(nicknameBytes, StandardCharsets.UTF_8);

            result = new NodeJoinResponse(nickname);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }
}
