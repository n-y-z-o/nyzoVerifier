package co.nyzo.verifier.messages.debug;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.BlockVote;
import co.nyzo.verifier.messages.MultilineTextResponse;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class UnfrozenBlockPoolStatusResponse implements MessageObject, MultilineTextResponse {

    private List<String> lines;

    public UnfrozenBlockPoolStatusResponse(Message request) {

        // This is a debug request, so it must be signed by the local verifier.
        if (ByteUtil.arraysAreEqual(request.getSourceNodeIdentifier(), Verifier.getIdentifier())) {

            // Get and score the blocks.
            List<Block> blocks = UnfrozenBlockManager.allUnfrozenBlocks();
            long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
            Map<Block, Long> chainScoreMap = new HashMap<>();
            for (Block block : blocks) {
                chainScoreMap.put(block, block.chainScore(frozenEdgeHeight));
            }

            // Sort the blocks.
            Collections.sort(blocks, new Comparator<Block>() {
                @Override
                public int compare(Block block1, Block block2) {
                    if (block1.getBlockHeight() == block2.getBlockHeight()) {
                        Long chainScore1 = chainScoreMap.get(block1);
                        Long chainScore2 = chainScoreMap.get(block2);
                        return chainScore1.compareTo(chainScore2);
                    } else {
                        return ((Long) block1.getBlockHeight()).compareTo(block2.getBlockHeight());
                    }
                }
            });

            // Produce the response.
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < blocks.size() && i < 100; i++) {
                Block block = blocks.get(i);
                if (block.getBlockHeight() > frozenEdgeHeight) {
                    long chainScore = chainScoreMap.get(block);
                    String chainScoreString = PrintUtil.printChainScore(chainScore);
                    BlockVote vote = BlockVoteManager.getLocalVoteForHeight(block.getBlockHeight());
                    String localVoteString = vote != null && ByteUtil.arraysAreEqual(block.getHash(), vote.getHash()) ?
                            "*" : "";
                    lines.add(block.getBlockHeight() + " (" + PrintUtil.superCompactPrintByteArray(block.getHash()) +
                            "/" + NicknameManager.get(block.getVerifierIdentifier()) + "): " + chainScoreString +
                            localVoteString);
                }
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
