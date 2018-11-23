package co.nyzo.verifier.messages.debug;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.MultilineTextResponse;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class BlacklistStatusResponse implements MessageObject, MultilineTextResponse {

    private List<String> lines;

    public BlacklistStatusResponse(Message request) {

        // This is a debug request, so it must be signed by the local verifier.
        if (ByteUtil.arraysAreEqual(request.getSourceNodeIdentifier(), Verifier.getIdentifier())) {

            List<String> lines = new ArrayList<>();
            lines.add("messages rejected: " + MeshListener.getNumberOfMessagesRejected());
            lines.add("messages accepted: " + MeshListener.getNumberOfMessagesAccepted());
            lines.add("blacklist size: " + BlacklistManager.getBlacklistSize());

            this.lines = lines;
        } else {
            this.lines = Arrays.asList("*** Unauthorized ***");
        }
    }

    public BlacklistStatusResponse(List<String> lines) {

        this.lines = lines;
    }

    public List<String> getLines() {
        return lines;
    }

    @Override
    public int getByteSize() {

        int byteSize = 1;  // list length
        for (String line : lines) {
            byteSize += FieldByteSize.string(line);
        }

        return byteSize;
    }

    @Override
    public byte[] getBytes() {

        byte[] result = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(result);

        buffer.put((byte) lines.size());
        for (String line : lines) {
            byte[] lineBytes = line.getBytes(StandardCharsets.UTF_8);
            buffer.putShort((short) lineBytes.length);
            buffer.put(lineBytes);
        }

        return result;
    }

    public static BlacklistStatusResponse fromByteBuffer(ByteBuffer buffer) {

        BlacklistStatusResponse result = null;

        try {
            int numberOfLines = buffer.get() & 0xff;
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < numberOfLines; i++) {
                short lineByteLength = buffer.getShort();
                byte[] lineBytes = new byte[lineByteLength];
                buffer.get(lineBytes);
                lines.add(new String(lineBytes, StandardCharsets.UTF_8));
            }

            result = new BlacklistStatusResponse(lines);

        } catch (Exception ignored) { }

        return result;
    }

    @Override
    public String toString() {
        return "[BlacklistStatusResponse(lines=" + lines.size() + ")]";
    }
}
