package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class BlockResponse implements MessageObject {

    private BalanceList initialBalanceList;
    private List<Block> blocks;

    public BlockResponse(long startBlockHeight, boolean includeInitialBalanceList) {

        BalanceList initialBalanceList = null;
        List<Block> blocks = new ArrayList<>();
        int totalByteSize = 0;
        boolean foundNullBlock = false;
        long blockHeight = startBlockHeight;
        while (totalByteSize < 50000 && !foundNullBlock) {
            Block block = BlockManager.frozenBlockForHeight(blockHeight);
            if (block == null) {
                foundNullBlock = true;
            } else {
                blocks.add(block);
                totalByteSize += block.getByteSize();
                if (blockHeight == startBlockHeight && includeInitialBalanceList) {
                    initialBalanceList = block.getBalanceList();
                }
            }

            blockHeight++;
        }

        System.out.println("built list of " + blocks.size() + " for request block size of " + startBlockHeight);

        this.initialBalanceList = initialBalanceList;
        this.blocks = blocks;
    }

    public BlockResponse(BalanceList initialBalanceList, List<Block> blocks) {

        this.initialBalanceList = initialBalanceList;
        this.blocks = blocks;
    }

    public BalanceList getInitialBalanceList() {
        return initialBalanceList;
    }

    public List<Block> getBlocks() {
        return blocks;
    }

    @Override
    public int getByteSize() {

        int byteSize = FieldByteSize.booleanField;  // boolean value indicating whether a balance list is included
        if (initialBalanceList != null) {
            byteSize += initialBalanceList.getByteSize();
        }

        byteSize += FieldByteSize.frozenBlockListLength;
        for (Block block : blocks) {
            byteSize += block.getByteSize();
        }

        return byteSize;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(initialBalanceList == null ? (byte) 0 : (byte) 1);
        if (initialBalanceList != null) {
            buffer.put(initialBalanceList.getBytes());
        }

        buffer.putShort((short) blocks.size());
        for (Block block : blocks) {
            buffer.put(block.getBytes());
        }

        return array;
    }

    public static BlockResponse fromByteBuffer(ByteBuffer buffer) {

        BlockResponse result = null;

        try {
            BalanceList initialBalanceList = null;
            if (buffer.get() == 1) {
                initialBalanceList = BalanceList.fromByteBuffer(buffer);
            }

            List<Block> blocks = new ArrayList<>();
            int numberOfBlocks = buffer.getShort() & 0xffff;
            for (int i = 0; i < numberOfBlocks; i++) {
                blocks.add(Block.fromByteBuffer(buffer));
            }

            result = new BlockResponse(initialBalanceList, blocks);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }
}
