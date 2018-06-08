package co.nyzo.verifier.messages;

import co.nyzo.verifier.BlockManager;
import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.MessageObject;

import java.nio.ByteBuffer;

public class PreviousHashResponse implements MessageObject {

    private long height;
    private byte[] hash;

    public PreviousHashResponse() {
        height = BlockManager.frozenEdgeHeight();
        hash = BlockManager.frozenBlockForHeight(height).getHash();
    }

    @Override
    public byte[] getBytes() {

        byte[] result = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(result);
        buffer.putLong(height);
        buffer.put(hash);

        return result;
    }

    @Override
    public int getByteSize() {
        return FieldByteSize.blockHeight + FieldByteSize.hash;
    }
}
