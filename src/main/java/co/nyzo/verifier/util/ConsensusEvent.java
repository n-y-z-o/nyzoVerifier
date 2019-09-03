package co.nyzo.verifier.util;

public class ConsensusEvent {

    private long timestamp;
    private long height;
    private Object data;

    public ConsensusEvent(long height, Object data) {
        this.timestamp = System.currentTimeMillis();
        this.height = height;
        this.data = data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getHeight() {
        return height;
    }

    public Object getData() {
        return data;
    }
}
