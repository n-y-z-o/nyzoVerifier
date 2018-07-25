package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;

import java.nio.ByteBuffer;

public class NewBlockMessage implements MessageObject, PortMessage {

    private Block block;
    private int port;

    public NewBlockMessage(Block block) {

        this.block = block;
        this.port = MeshListener.getPort();
    }

    public NewBlockMessage(Block block, int port) {

        this.block = block;
        this.port = port;
    }

    public Block getBlock() {
        return block;
    }

    public int getPort() {
        return port;
    }

    @Override
    public int getByteSize() {

        return block.getByteSize() + FieldByteSize.port;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(block.getBytes());
        buffer.putInt(port);

        return array;
    }

    public static NewBlockMessage fromByteBuffer(ByteBuffer buffer) {

        NewBlockMessage result = null;

        try {
            Block block = Block.fromByteBuffer(buffer);
            int port = buffer.getInt();

            result = new NewBlockMessage(block, port);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }
}
