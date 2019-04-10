package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.NotificationUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.TestnetUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class StatusResponse implements MessageObject {

    private final List<String> lines;

    public StatusResponse(byte[] requesterIdentifier) {

        long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
        Block frozenEdge = BlockManager.frozenBlockForHeight(frozenEdgeHeight);

        List<String> lines = new ArrayList<>();
        lines.add("nickname: " + Verifier.getNickname() + (Verifier.isPaused() ? "*** PAUSED ***" : ""));
        if (TestnetUtil.testnet) {
            lines.add("*** IN TESTNET MODE ***");
        }
        lines.add("version: " + Version.getVersion());
        lines.add("ID: " + PrintUtil.compactPrintByteArray(Verifier.getIdentifier()));
        lines.add("mesh: " + NodeManager.getNumberOfActiveIdentifiers() + " total, " +
                NodeManager.getNumberOfActiveCycleIdentifiers() + " in cycle");
        lines.add("cycle length: " + BlockManager.currentCycleLength() + (BlockManager.inGenesisCycle() ? "(G)" : ""));
        lines.add("transactions: " + TransactionPool.transactionPoolSize());
        lines.add("retention edge: " + BlockManager.getRetentionEdgeHeight());
        lines.add("trailing edge: " + BlockManager.getTrailingEdgeHeight());
        lines.add("frozen edge: " + frozenEdgeHeight + " (" + (frozenEdge == null ? "null" :
                NicknameManager.get(frozenEdge.getVerifierIdentifier())) + ")");
        lines.add("open edge: " + BlockManager.openEdgeHeight(false));
        lines.add("blocks transmitted/created: " + Verifier.getBlockCreationInformation());
        lines.add("votes requested: " + BlockVoteManager.getNumberOfVotesRequested());
        lines.add("block vote: " + UnfrozenBlockManager.getVoteDescription());
        lines.add("last removal height: " + BlockManager.getLastVerifierRemovalHeight());
        List<Long> unfrozenBlockHeights = new ArrayList<>(UnfrozenBlockManager.unfrozenBlockHeights());
        Collections.sort(unfrozenBlockHeights);
        for (int i = 0; i < 7 && i < unfrozenBlockHeights.size(); i++) {
            if (i == 3 && unfrozenBlockHeights.size() > 7) {
                lines.add("...");
            } else {
                long height = i < 3 || unfrozenBlockHeights.size() <= 7 ? unfrozenBlockHeights.get(i) :
                        unfrozenBlockHeights.get(unfrozenBlockHeights.size() - 7 + i);
                if (height > frozenEdgeHeight) {
                    String heightString = "+" + (height - frozenEdgeHeight);
                    lines.add("- h: " + heightString + ", n: " +
                            UnfrozenBlockManager.numberOfBlocksAtHeight(height) +
                            ", v: " + BlockVoteManager.votesAtHeight(height));
                }
            }
        }
        lines.add("requester identifier: " + PrintUtil.compactPrintByteArray(requesterIdentifier));

        if (ByteUtil.arraysAreEqual(requesterIdentifier, Verifier.getIdentifier())) {
            lines.add("new timestamp: " + Verifier.newestTimestampAge(2));
            lines.add("old timestamp: " + Verifier.oldestTimestampAge());
            lines.add("blocks: " + BlockManagerMap.mapInformation());
            lines.add("balance lists: " + BalanceListManager.mapInformation());
            lines.add("node-joins sent: " + NodeManager.getNodeJoinRequestsSent());
            lines.add("memory (min/max/avg): " + MemoryMonitor.getMemoryStats());

            Map<Long, Integer> thresholdOverrides = UnfrozenBlockManager.getThresholdOverrides();
            for (Long height : thresholdOverrides.keySet()) {
                lines.add("override @+" + (height - frozenEdgeHeight) + ": " + thresholdOverrides.get(height) + "%");
            }

            Map<Long, byte[]> hashOverrides = UnfrozenBlockManager.getHashOverrides();
            for (Long height : hashOverrides.keySet()) {
                lines.add("override @+" + (height - frozenEdgeHeight) + ": " +
                        PrintUtil.superCompactPrintByteArray(hashOverrides.get(height)));
            }

            // The notification budget is initialized to -1. A non-negative value is only set if the notification
            // endpoint is configured, so this will not be displayed if notifications are not used.
            long notificationBudget = NotificationUtil.getCurrentBudget();
            if (notificationBudget >= 0) {
                lines.add("notif. budget: " + notificationBudget);
            }

            // This shows which in-cycle verifiers currently have no active mesh nodes.
            lines.add("missing in-cycle verifiers: " + NodeManager.getMissingInCycleVerifiers());
        }

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

    public static void print() {

        System.out.println("********************");
        StatusResponse statusResponse = new StatusResponse(Verifier.getIdentifier());
        for (String line : statusResponse.getLines()) {
            System.out.println(line);
        }
        System.out.println("********************");
    }
}
