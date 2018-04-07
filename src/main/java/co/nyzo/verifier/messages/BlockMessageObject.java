package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;

import java.nio.ByteBuffer;

public class BlockMessageObject implements MessageObject {

    private Block block;

    public BlockMessageObject(Block block) {

        this.block = block;
    }

    public Block getBlock() {
        return block;
    }

    @Override
    public int getByteSize() {
        return block.getByteSize() +
                block.getBalanceList().getByteSize();
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(block.getBytes());
        buffer.put(block.getBalanceList().getBytes());

        return array;
    }

    public static BlockMessageObject fromByteBuffer(ByteBuffer buffer) {

        BlockMessageObject result = null;

        try {
            Block block = Block.fromByteBuffer(buffer);
            block.setBalanceList(BalanceList.fromByteBuffer(buffer));

            result = new BlockMessageObject(block);

        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }
}
