package co.nyzo.verifier.messages;

import co.nyzo.verifier.Block;
import co.nyzo.verifier.BlockManager;
import co.nyzo.verifier.MessageObject;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class NodeJoinResponse implements MessageObject {

    private List<Block> blocks;

    public NodeJoinResponse() {

        // This response returns the Genesis block and the five highest frozen blocks.
        blocks = new ArrayList<>();

        Block genesisBlock = BlockManager.frozenBlockForHeight(0);
        if (genesisBlock != null) {
            blocks.add(genesisBlock);
        }

        long highestBlockFrozen = BlockManager.highestBlockFrozen();
        for (long i = Math.max(1L, highestBlockFrozen - 4L); i <= highestBlockFrozen; i++) {
            Block block = BlockManager.frozenBlockForHeight(i);
            if (block != null) {
                blocks.add(block);
            }
        }
    }

    private NodeJoinResponse(List<Block> blocks) {
        this.blocks = blocks;
    }

    public List<Block> getBlocks() {
        return blocks;
    }

    @Override
    public int getByteSize() {

        int size = Short.BYTES;
        for (Block block : blocks) {
            size += block.getByteSize();
        }

        return size;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.putShort((short) blocks.size());
        for (Block block : blocks) {
            buffer.put(block.getBytes());
        }

        return array;
    }

    public static NodeJoinResponse fromByteBuffer(ByteBuffer buffer) {

        NodeJoinResponse result = null;

        try {
            short numberOfBlocks = buffer.getShort();
            List<Block> blocks = new ArrayList<>();
            for (int i = 0; i < numberOfBlocks; i++) {
                blocks.add(Block.fromByteBuffer(buffer));
            }

            result = new NodeJoinResponse(blocks);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }
}
