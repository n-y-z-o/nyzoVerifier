package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class StatusResponse implements MessageObject {

    private static long previousStatusTimestamp = 0L;
    private static List<String> previousLines;

    private List<String> lines;
    private static final Map<String, String> extraFields = new HashMap<>();

    public StatusResponse() {

        // To avoid unnecessary work, do not produce a status response more frequently than every four seconds.
        long currentTimestamp = System.currentTimeMillis();
        if (currentTimestamp - previousStatusTimestamp < 4000L) {
            this.lines = previousLines;
        } else {

            long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
            Block frozenEdge = BlockManager.frozenBlockForHeight(frozenEdgeHeight);

            int meshSize = NodeManager.getMeshSize();
            int activeMeshSize = NodeManager.getActiveMeshSize();
            int inactiveMeshSize = meshSize - activeMeshSize;

            List<String> lines = new ArrayList<>();
            lines.add("nickname: " + Verifier.getNickname() + (Verifier.isPaused() ? "*** PAUSED ***" : ""));
            lines.add("version: " + Version.getVersion());
            lines.add("ID: " + PrintUtil.compactPrintByteArray(Verifier.getIdentifier()));
            lines.add("mesh: " + activeMeshSize + " active, " + inactiveMeshSize + " inactive");
            lines.add("cycle length: " + BlockManager.currentCycleLength() +
                    (BlockManager.inGenesisCycle() ? "(G)" : ""));
            lines.add("transactions: " + TransactionPool.transactionPoolSize());
            lines.add("retention edge: " + BlockManager.getRetentionEdgeHeight());
            lines.add("trailing edge: " + BlockManager.getTrailingEdgeHeight());
            lines.add("frozen edge: " + frozenEdgeHeight + " (" + (frozenEdge == null ? "null" :
                    PrintUtil.superCompactPrintByteArray(frozenEdge.getHash()) + ", " +
                    NicknameManager.get(frozenEdge.getVerifierIdentifier())) + ")");
            lines.add("leading edge: " + UnfrozenBlockManager.leadingEdgeHeight());
            lines.add("open edge: " + BlockManager.openEdgeHeight(false));
            lines.add("blocks transmitted/created: " + Verifier.getBlockCreationInformation());
            List<Long> unfrozenBlockHeights = new ArrayList<>(UnfrozenBlockManager.unfrozenBlockHeights());
            Collections.sort(unfrozenBlockHeights);
            for (int i = 0; i < 7 && i < unfrozenBlockHeights.size(); i++) {
                if (i == 3 && unfrozenBlockHeights.size() > 7) {
                    lines.add("...");
                } else {
                    long height = i < 3 || unfrozenBlockHeights.size() <= 7 ? unfrozenBlockHeights.get(i) :
                            unfrozenBlockHeights.get(unfrozenBlockHeights.size() - 7 + i);
                    String heightString = "+" + (height - frozenEdgeHeight);
                    lines.add("- h: " + heightString + ", n: " + UnfrozenBlockManager.numberOfBlocksAtHeight(height) +
                            ", v: " + BlockVoteManager.votesAtHeight(height) +
                            ", s: " + PrintUtil.printChainScore(UnfrozenBlockManager.bestScoreForHeight(height)) +
                            ", t: " + UnfrozenBlockManager.votingScoreThresholdForHeight(height));
                }
            }
            lines.add("new timestamp: " + Verifier.newestTimestampAge(2));
            lines.add("old timestamp: " + Verifier.oldestTimestampAge());
            lines.add("block map: " + BlockManagerMap.mapInformation());
            lines.add("balance list map: " + BalanceListManager.mapInformation());
            lines.add("avg. work/balance list: " + BalanceListManager.averageWork());

            Map<String, String> extraFields = getExtraFields();
            for (String key : extraFields.keySet()) {
                lines.add(key + ": " + extraFields.get(key));
            }

            this.lines = lines;
            previousLines = lines;
            previousStatusTimestamp = currentTimestamp;
        }
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

        byte[] result = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(result);

        buffer.put((byte) lines.size());
        for (String line : lines) {
            Message.putString(line, buffer);
        }

        return result;
    }

    public static StatusResponse fromByteBuffer(ByteBuffer buffer) {

        StatusResponse result = null;

        try {
            int numberOfLines = buffer.get() & 0xff;
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < numberOfLines; i++) {
                lines.add(Message.getString(buffer));
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

    private static synchronized Map<String, String> getExtraFields() {

        return new HashMap<>(extraFields);
    }

    public static synchronized void setField(String key, String value) {

        if (value == null) {
            extraFields.remove(key);
        } else {
            extraFields.put(key, value);
        }

        if (extraFields.size() > 10) {
            extraFields.clear();
        }
    }

    public static void print() {

        System.out.println("********************");
        StatusResponse statusResponse = new StatusResponse();
        for (String line : statusResponse.getLines()) {
            System.out.println(line);
        }
        System.out.println("********************");
    }
}
