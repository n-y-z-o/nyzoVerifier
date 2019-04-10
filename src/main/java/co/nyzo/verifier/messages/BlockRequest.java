package co.nyzo.verifier.messages;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.MessageObject;

import java.nio.ByteBuffer;

public class BlockRequest implements MessageObject {

    private final long startHeight;
    private final long endHeight;
    private final boolean includeBalanceList;

    public BlockRequest(long startHeight, long endHeight, boolean includeBalanceList) {

        this.startHeight = startHeight;
        this.endHeight = endHeight;
        this.includeBalanceList = includeBalanceList;
    }

    public long getStartHeight() {
        return startHeight;
    }

    public long getEndHeight() {
        return endHeight;
    }

    public boolean includeBalanceList() {
        return includeBalanceList;
    }

    @Override
    public int getByteSize() {
        return FieldByteSize.blockHeight * 2 + FieldByteSize.booleanField;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.putLong(startHeight);
        buffer.putLong(endHeight);
        buffer.put(includeBalanceList ? (byte) 1 : (byte) 0);

        return array;
    }

    public static BlockRequest fromByteBuffer(ByteBuffer buffer) {

        BlockRequest result = null;

        try {
            long startHeight = buffer.getLong();
            long endHeight = buffer.getLong();
            boolean includeBalanceList = buffer.get() == 1;

            result = new BlockRequest(startHeight, endHeight, includeBalanceList);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }
}
