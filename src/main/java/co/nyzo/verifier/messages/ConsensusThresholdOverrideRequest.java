package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;

import java.nio.ByteBuffer;

public class ConsensusThresholdOverrideRequest implements MessageObject {

    private long height;
    private int thresholdPercent;

    public ConsensusThresholdOverrideRequest(long height, int thresholdPercent) {

        this.height = height;
        this.thresholdPercent = thresholdPercent;
    }

    public long getHeight() {
        return height;
    }

    public int getThresholdPercent() {
        return thresholdPercent;
    }

    @Override
    public int getByteSize() {

        return FieldByteSize.blockHeight +     // height
                FieldByteSize.unnamedInteger;  // threshold percent
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.putLong(height);
        buffer.putInt(thresholdPercent);

        return array;
    }

    public static ConsensusThresholdOverrideRequest fromByteBuffer(ByteBuffer buffer) {

        long height = buffer.getLong();
        int thresholdPercent = buffer.getInt();

        return new ConsensusThresholdOverrideRequest(height, thresholdPercent);
    }

    @Override
    public String toString() {
        return "[ConsensusThresholdOverrideRequest(height=" + height + ", thresholdPercent=" + thresholdPercent + "]";
    }
}
