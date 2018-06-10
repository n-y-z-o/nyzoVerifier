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

        long frozenEdgeHeight = BlockManager.frozenEdgeHeight();

        List<String> lines = new ArrayList<>();
        lines.add("nickname: " + Verifier.getNickname());
        lines.add("version: " + Verifier.getVersion());
        lines.add("ID: " + PrintUtil.compactPrintByteArray(Verifier.getIdentifier()));
        lines.add("mesh: " + NodeManager.getMesh().size() + " active, " + NodeManager.numberOfInactiveNodes() +
                " inactive");
        lines.add("transactions: " + TransactionPool.transactionPoolSize());
        lines.add("frozen edge: " + frozenEdgeHeight);
        lines.add("leading edge: " + ChainOptionManager.leadingEdgeHeight());
        lines.add("open edge: " + BlockManager.openEdgeHeight(false));
        List<Long> unfrozenBlockHeights = new ArrayList<>(ChainOptionManager.unfrozenBlockHeights());
        Collections.sort(unfrozenBlockHeights);
        for (int i = 0; i < 7 && i < unfrozenBlockHeights.size(); i++) {
            if (i == 3 && unfrozenBlockHeights.size() > 7) {
                lines.add("...");
            } else {
                long height = i < 3 || unfrozenBlockHeights.size() <= 7 ? unfrozenBlockHeights.get(i) :
                        unfrozenBlockHeights.get(unfrozenBlockHeights.size() - 7 + i);
                String heightString = "f+" + (height - frozenEdgeHeight);
                lines.add("- h: " + heightString + ", n: " + ChainOptionManager.numberOfBlocksAtHeight(height) +
                        ", v: " + BlockVoteManager.numberOfVotesAtHeight(height) +
                        ", s: " + ChainOptionManager.bestScoreForHeight(height) +
                        ", t: " + ChainOptionManager.votingScoreThresholdForHeight(height));
            }
        }
        lines.add("new timestamp: " + Verifier.newestTimestampAge(2));
        lines.add("old timestamp: " + Verifier.oldestTimestampAge());

        this.lines = lines;
    }

    private StatusResponse(List<String> lines) {
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
