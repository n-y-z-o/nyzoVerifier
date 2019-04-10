package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;

import java.nio.ByteBuffer;

public class MissingBlockResponse implements MessageObject {

    private Block block;

    public MissingBlockResponse(long height, byte[] hash) {

        long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
        if (height <= frozenEdgeHeight) {
            Block frozenBlock = BlockManager.frozenBlockForHeight(height);
            if (frozenBlock != null && ByteUtil.arraysAreEqual(hash, frozenBlock.getHash())) {
                block = frozenBlock;
            }
        } else {
            block = UnfrozenBlockManager.unfrozenBlockAtHeight(height, hash);
        }
    }

    private MissingBlockResponse(Block block) {

        this.block = block;
    }

    public Block getBlock() {

        return block;
    }

    @Override
    public int getByteSize() {

        return FieldByteSize.booleanField + (block == null ? 0 : block.getByteSize());
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(block == null ? (byte) 0 : (byte) 1);
        if (block != null) {
            buffer.put(block.getBytes());
        }

        return array;
    }

    public static MissingBlockResponse fromByteBuffer(ByteBuffer buffer) {

        MissingBlockResponse result = null;

        try {
            Block block = null;
            if (buffer.get() == 1) {
                block = Block.fromByteBuffer(buffer);
            }

            result = new MissingBlockResponse(block);

        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    @Override
    public String toString() {
        return "MissingBlockResponse[block=" + block + "]";
    }
}
