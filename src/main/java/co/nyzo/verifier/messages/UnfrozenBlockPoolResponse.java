package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class UnfrozenBlockPoolResponse implements MessageObject {

    private List<Block> blocks;

    public UnfrozenBlockPoolResponse(Message request) {

        // This is a debug request, so it must be signed by the local verifier.
        if (ByteUtil.arraysAreEqual(request.getSourceNodeIdentifier(), Verifier.getIdentifier())) {
            this.blocks = ChainOptionManager.allUnfrozenBlocks();
        } else {
            this.blocks = new ArrayList<>();
        }
    }

    public UnfrozenBlockPoolResponse(List<Block> blocks) {

        this.blocks = blocks;
    }

    public List<Block> getBlocks() {
        return blocks;
    }

    @Override
    public int getByteSize() {

        int byteSize = FieldByteSize.unfrozenBlockPoolLength;
        for (Block block : blocks) {
            byteSize += block.getByteSize();
        }

        return byteSize;
    }

    @Override
    public byte[] getBytes() {

        int size = getByteSize();
        byte[] result = new byte[size];
        ByteBuffer buffer = ByteBuffer.wrap(result);

        buffer.putShort((short) blocks.size());
        for (Block block : blocks) {
            buffer.put(block.getBytes());
        }

        return result;
    }

    public static UnfrozenBlockPoolResponse fromByteBuffer(ByteBuffer buffer) {

        UnfrozenBlockPoolResponse result = null;

        try {
            short numberOfBlocks = buffer.getShort();
            List<Block> blocks = new ArrayList<>();
            for (int i = 0; i < numberOfBlocks; i++) {
                blocks.add(Block.fromByteBuffer(buffer));
            }

            result = new UnfrozenBlockPoolResponse(blocks);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("[UnfrozenBlockPoolResponse(blocks=" + blocks.size() + "]");

        return result.toString();
    }
}
