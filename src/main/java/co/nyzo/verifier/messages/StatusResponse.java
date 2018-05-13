package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StatusResponse implements MessageObject {

    private List<String> lines;

    public StatusResponse() {

        List<String> lines = new ArrayList<>();
        lines.add("nickname: " + Verifier.getNickname());
        lines.add("ID: " + PrintUtil.compactPrintByteArray(Verifier.getIdentifier()));
        lines.add("mesh: " + NodeManager.getMesh().size() + " active, " + NodeManager.numberOfInactiveNodes() +
                " inactive");
        lines.add("frozen edge: " + BlockManager.highestBlockFrozen());
        lines.add("leading edge: " + ChainOptionManager.leadingEdgeHeight());
        List<Long> unfrozenBlockHeights = new ArrayList<>(ChainOptionManager.unfrozenBlockHeights());
        Collections.sort(unfrozenBlockHeights);
        for (int i = 0; i < 7 && i < unfrozenBlockHeights.size(); i++) {
            if (i == 3 && unfrozenBlockHeights.size() > 7) {
                lines.add("...");
            } else {
                long height = i < 3 || unfrozenBlockHeights.size() <= 7 ? unfrozenBlockHeights.get(i) :
                        unfrozenBlockHeights.get(unfrozenBlockHeights.size() - i + 3);
                lines.add("- height: " + height + ", n: " + ChainOptionManager.numberOfBlocksAtHeight(height));
            }
        }
        lines.add("timestamp age: " + Verifier.timestampAge());

        this.lines = lines;
    }

    public StatusResponse(List<String> lines) {
        this.lines = lines;
    }

    public List<String> getLines() {
        return lines;
    }

    @Override
    public int getByteSize() {
        int byteSize = 1;  // list size
        for (String line : lines) {
            byteSize += FieldByteSize.string(line);
        }

        return byteSize;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put((byte) lines.size());
        for (String line : lines) {
            byte[] lineBytes = line.getBytes(StandardCharsets.UTF_8);
            buffer.putShort((short) lineBytes.length);
            buffer.put(lineBytes);
        }

        return array;
    }

    public static StatusResponse fromByteBuffer(ByteBuffer buffer) {

        StatusResponse result = null;

        try {
            int numberOfLines = buffer.get() & 0xff;
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < numberOfLines; i++) {
                short lineByteLength = buffer.getShort();
                byte[] lineBytes = new byte[lineByteLength];
                buffer.get(lineBytes);
                lines.add(new String(lineBytes, StandardCharsets.UTF_8));
            }

            result = new StatusResponse(lines);

        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    @Override
    public String toString() {
        return "[StatusResponse(n=" + lines.size() + ")]";
    }
}
