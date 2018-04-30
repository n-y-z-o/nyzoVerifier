package co.nyzo.verifier.messages;

import co.nyzo.verifier.BalanceList;
import co.nyzo.verifier.Block;
import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.MessageObject;

import java.nio.ByteBuffer;

public class BlockResponse implements MessageObject {

    private Block block;
    private boolean includeBalanceList;

    public BlockResponse(Block block, boolean includeBalanceList) {

        this.block = block;
        this.includeBalanceList = includeBalanceList;
    }

    public Block getBlock() {
        return block;
    }

    public boolean includeBalanceList() {
        return includeBalanceList;
    }

    @Override
    public int getByteSize() {

        int byteSize = block.getByteSize() + FieldByteSize.booleanField;
        if (includeBalanceList && block.getBalanceList() != null) {
            byteSize += block.getBalanceList().getByteSize();
        }

        return byteSize;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(block.getBytes());

        boolean hasBalanceList = includeBalanceList && block.getBalanceList() != null;
        buffer.put(hasBalanceList ? (byte) 1 : (byte) 0);
        if (hasBalanceList) {
            buffer.put(block.getBalanceList().getBytes());
        }

        return array;
    }

    public static BlockResponse fromByteBuffer(ByteBuffer buffer) {

        BlockResponse result = null;

        try {
            Block block = Block.fromByteBuffer(buffer);
            boolean hasBalanceList = buffer.get() == 1;
            if (hasBalanceList) {
                block.setBalanceList(BalanceList.fromByteBuffer(buffer));
            }

            result = new BlockResponse(block, hasBalanceList);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }
}
