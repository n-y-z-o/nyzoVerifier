package co.nyzo.verifier.messages;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.MessageObject;

import java.nio.ByteBuffer;

public class TimestampResponse implements MessageObject {

    private final long timestamp;

    public TimestampResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    private TimestampResponse(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public int getByteSize() {
        return FieldByteSize.timestamp;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.putLong(timestamp);

        return array;
    }

    public static TimestampResponse fromByteBuffer(ByteBuffer buffer) {

        TimestampResponse result = null;

        try {
            long timestamp = buffer.getLong();
            result = new TimestampResponse(timestamp);

        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    @Override
    public String toString() {
        return "TimestampResponse[" + timestamp + "]";
    }
}
