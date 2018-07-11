package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class UnfrozenBlockPoolStatusResponse implements MessageObject {

    private List<String> lines;

    public UnfrozenBlockPoolStatusResponse(Message request) {

        // This is a debug request, so it must be signed by the local verifier.
        if (ByteUtil.arraysAreEqual(request.getSourceNodeIdentifier(), Verifier.getIdentifier())) {

            List<Block> blocks = UnfrozenBlockManager.allUnfrozenBlocks();
            Collections.sort(blocks, new Comparator<Block>() {
                @Override
                public int compare(Block block1, Block block2) {
                    if (block1.getBlockHeight() == block2.getBlockHeight()) {
                        String nickname1 = NicknameManager.get(block1.getVerifierIdentifier());
                        String nickname2 = NicknameManager.get(block2.getVerifierIdentifier());
                        return nickname1.compareTo(nickname2);
                    } else {
                        return ((Long) block1.getBlockHeight()).compareTo(block2.getBlockHeight());
                    }
                }
            });
            long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < blocks.size() && i < 100; i++) {
                Block block = blocks.get(i);
                long chainScore = block.chainScore(frozenEdgeHeight);
                String chainScoreString = chainScore == Long.MAX_VALUE ? "H" : (chainScore == Long.MAX_VALUE - 1 ?
                        "H-1" : chainScore + "");
                lines.add(block.getBlockHeight() + " (" + NicknameManager.get(block.getVerifierIdentifier()) + "): " +
                        chainScoreString);
            }

            this.lines = lines;
        } else {
            this.lines = new ArrayList<>();
        }
    }

    public UnfrozenBlockPoolStatusResponse(List<String> lines) {

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

    public static UnfrozenBlockPoolStatusResponse fromByteBuffer(ByteBuffer buffer) {

        UnfrozenBlockPoolStatusResponse result = null;

        try {
            int numberOfLines = buffer.get() & 0xff;
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < numberOfLines; i++) {
                short lineByteLength = buffer.getShort();
                byte[] lineBytes = new byte[lineByteLength];
                buffer.get(lineBytes);
                lines.add(new String(lineBytes, StandardCharsets.UTF_8));
            }

            result = new UnfrozenBlockPoolStatusResponse(lines);

        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("[UnfrozenBlockPoolStatusResponse(lines=" + lines.size() + ")]");

        return result.toString();
    }
}
