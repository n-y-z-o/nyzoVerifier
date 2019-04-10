package co.nyzo.verifier.messages;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.MessageObject;

import java.nio.ByteBuffer;

public class PauseRequest implements MessageObject {

    private final boolean paused;

    private PauseRequest(boolean paused) {
        this.paused = paused;
    }

    private boolean isPaused() {
        return paused;
    }

    @Override
    public int getByteSize() {
        return FieldByteSize.booleanField;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put((byte) (paused ? 1 : 0));

        return array;
    }

    public static PauseRequest fromByteBuffer(ByteBuffer buffer) {

        PauseRequest result = null;

        try {
            boolean paused = buffer.get() == 1;
            result = new PauseRequest(paused);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    @Override
    public String toString() {

        return "[PauseRequest(paused=" + isPaused() + ")]";
    }
}
