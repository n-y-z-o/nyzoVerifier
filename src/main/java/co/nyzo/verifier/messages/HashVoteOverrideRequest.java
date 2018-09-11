package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;

public class HashVoteOverrideRequest implements MessageObject {

    private long height;
    private byte[] hash;

    public HashVoteOverrideRequest(long height, byte[] hash) {

        this.height = height;
        this.hash = hash;
    }

    public long getHeight() {
        return height;
    }

    public byte[] getHash() {
        return hash;
    }

    @Override
    public int getByteSize() {

        return FieldByteSize.blockHeight +    // height
                FieldByteSize.hash;           // hash
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.putLong(height);
        buffer.put(hash);

        return array;
    }

    public static HashVoteOverrideRequest fromByteBuffer(ByteBuffer buffer) {

        long height = buffer.getLong();
        byte[] hash = new byte[FieldByteSize.hash];
        buffer.get(hash);

        return new HashVoteOverrideRequest(height, hash);
    }

    @Override
    public String toString() {
        return "[HashVoteOverrideRequest(height=" + height + ", hash=" + PrintUtil.superCompactPrintByteArray(hash) +
                "]";
    }
}
