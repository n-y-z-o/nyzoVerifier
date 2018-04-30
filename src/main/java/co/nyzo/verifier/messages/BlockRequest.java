package co.nyzo.verifier.messages;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.MessageObject;

import java.nio.ByteBuffer;

public class BlockRequest implements MessageObject {

    private long blockHeight;
    private byte[] blockHash;
    private boolean includeBalanceList;

    public BlockRequest(long blockHeight, byte[] blockHash, boolean includeBalanceList) {

        this.blockHeight = blockHeight;
        this.blockHash = blockHash;
        this.includeBalanceList = includeBalanceList;
    }

    public long getBlockHeight() {
        return blockHeight;
    }

    public byte[] getBlockHash() {
        return blockHash;
    }

    public boolean includeBalanceList() {
        return includeBalanceList;
    }

    @Override
    public int getByteSize() {
        return FieldByteSize.blockHeight + FieldByteSize.hash + FieldByteSize.booleanField;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.putLong(blockHeight);
        buffer.put(blockHash);
        buffer.put(includeBalanceList ? (byte) 1 : (byte) 0);

        return array;
    }

    public static BlockRequest fromByteBuffer(ByteBuffer buffer) {

        BlockRequest result = null;

        try {
            long blockHeight = buffer.getLong();
            byte[] hash = new byte[FieldByteSize.hash];
            buffer.get(hash);
            boolean includeBalanceList = buffer.get() == 1;

            result = new BlockRequest(blockHeight, hash, includeBalanceList);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }
}
