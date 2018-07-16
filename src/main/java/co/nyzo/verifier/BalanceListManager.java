package co.nyzo.verifier;

import co.nyzo.verifier.util.NotificationUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class BalanceListManager {

    private static long totalQueries = 0;
    private static long totalWork = 0;

    // TODO: remove these; they are for debugging only
    private static BalanceList genesisList = null;
    private static BalanceList retentionEdgeList = null;
    private static BalanceList frozenEdgeList = null;

    private static final long maximumMapSize = 6;

    // This is a map from balance list hash to balance list.
    private static final Map<ByteBuffer, BalanceList> balanceListMap = new HashMap<>();

    public static synchronized BalanceList balanceListForBlock(Block block) {

        // Only proceed if the block is at or past the retention window start height or is the Genesis block.
        BalanceList balanceList = null;
        if (block.getBlockHeight() >= BlockManager.getRetentionEdgeHeight() || block.getBlockHeight() == 0) {

            totalQueries++;

            // First, try to get the balance list from the map.
            balanceList = balanceListMap.get(ByteBuffer.wrap(block.getBalanceListHash()));

            // If the balance list was not in the map, try to derive it.
            if (balanceList == null) {

                if (block.getBlockHeight() == 0L) {  // special case for Genesis block

                    balanceList = Block.balanceListForNextBlock(null, null, block.getTransactions(),
                            block.getVerifierIdentifier());
                    registerBalanceList(balanceList);

                    totalWork++;

                    if (!ByteUtil.arraysAreEqual(balanceList.getHash(), block.getBalanceListHash())) {
                        System.err.println("for Genesis block, derived hash: " +
                                PrintUtil.compactPrintByteArray(balanceList.getHash()) +
                                ", specified hash: " +
                                PrintUtil.compactPrintByteArray(block.getBalanceListHash()));
                    }

                } else {  // general case

                    // Step back to previous blocks until we are able to find a balance list that we have.
                    Block startBlock = block;
                    List<Block> blocks = new ArrayList<>(Arrays.asList(startBlock));
                    BalanceList startBalanceList = null;
                    while (startBlock != null && startBalanceList == null) {
                        startBlock = startBlock.getPreviousBlock();
                        if (startBlock != null) {
                            blocks.add(0, startBlock);
                            startBalanceList = balanceListMap.get(ByteBuffer.wrap(startBlock.getBalanceListHash()));
                        }
                    }

                    if (startBalanceList != null) {

                        totalWork += blocks.size();

                        // TODO: remove this notification
                        StringBuilder availableHeights = new StringBuilder();
                        String separator = "";
                        for (BalanceList cachedBalanceList : balanceListMap.values()) {
                            availableHeights.append(separator).append(cachedBalanceList.getBlockHeight());
                            separator = ",";
                        }
                        if (blocks.size() > 20) {
                            NotificationUtil.send("built list of size " + blocks.size() + " to derive balance list " +
                                    "for block " + block.getBlockHeight() + " on " + Verifier.getNickname() +
                                    " because the only heights available are " + availableHeights);
                        }

                        balanceList = startBalanceList;
                        for (int i = 0; i < blocks.size() - 1; i++) {

                            balanceList = Block.balanceListForNextBlock(blocks.get(i), balanceList,
                                    blocks.get(i + 1).getTransactions(), blocks.get(i + 1).getVerifierIdentifier());

                            // TODO: remove this check
                            if (!ByteUtil.arraysAreEqual(balanceList.getHash(),
                                    blocks.get(i + 1).getBalanceListHash())) {
                                NotificationUtil.send("after iteration " + i + ", derived hash: " +
                                        PrintUtil.compactPrintByteArray(balanceList.getHash()) +
                                        ", specified hash: " +
                                        PrintUtil.compactPrintByteArray(blocks.get(i + 1).getBalanceListHash()) +
                                        " on " + Verifier.getNickname());
                            }
                        }

                        registerBalanceList(balanceList);
                    }
                }
            }
        } else {
            NotificationUtil.send("trying to get balance list for height " + block.getBlockHeight() +
                    " when trailing edge height is " + BlockManager.getTrailingEdgeHeight() + " on " +
                    Verifier.getNickname());
        }

        return balanceList;
    }

    public static void printBalanceList(BalanceList balanceList) {

        System.out.println();
        if (balanceList == null) {
            System.out.println("***** balance list is null *****");
        } else {
            System.out.println("===== balance list " + balanceList.getBlockHeight() + " =====");
            System.out.println("hash: " + PrintUtil.compactPrintByteArray(balanceList.getHash()));

            for (int i = 0; i < balanceList.getPreviousVerifiers().size(); i++) {
                System.out.println("previous verifier " + i + ": " +
                        PrintUtil.compactPrintByteArray(balanceList.getPreviousVerifiers().get(i)));
            }

            for (int i = 0; i < balanceList.getItems().size(); i++) {
                BalanceListItem item = balanceList.getItems().get(i);
                System.out.println("item " + i + ": " + PrintUtil.compactPrintByteArray(item.getIdentifier()) + ", " +
                        item.getBalance());
            }
        }
        System.out.println();
    }

    public static synchronized void registerBalanceList(BalanceList balanceList) {

        if (balanceList != null) {
            balanceListMap.put(ByteBuffer.wrap(balanceList.getHash()), balanceList);

            // If the map is too large, remove less-used entries, keeping the frozen edge and the retention edge.
            // Also keep the Genesis list, if present.
            if (balanceListMap.size() > maximumMapSize) {

                // Find the balance lists for the frozen and retention edges.
                long retentionEdgeHeight = BlockManager.getRetentionEdgeHeight();
                long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
                BalanceList genesisList = null;
                BalanceList retentionEdgeList = null;
                BalanceList frozenEdgeList = null;
                for (BalanceList item : balanceListMap.values()) {
                    long itemHeight = item.getBlockHeight();

                    // Find the Genesis list. This is not required.
                    if (itemHeight == 0) {
                        genesisList = item;
                    }

                    // Find the highest balance list at or before the retention edge. This is necessary for
                    // bootstrapping new verifiers.
                    if (itemHeight <= retentionEdgeHeight &&
                            (retentionEdgeList == null || itemHeight > retentionEdgeList.getBlockHeight())) {
                        retentionEdgeList = item;
                    }

                    // Find the highest balance list at or before the frozen edge. This is necessary for creating new
                    // blocks.
                    if (itemHeight <= frozenEdgeHeight &&
                            (frozenEdgeList == null || itemHeight > frozenEdgeList.getBlockHeight())) {
                        frozenEdgeList = item;
                    }
                }

                if (retentionEdgeList != null && frozenEdgeList != null) {

                    balanceListMap.clear();
                    if (genesisList != null) {
                        balanceListMap.put(ByteBuffer.wrap(genesisList.getHash()), genesisList);
                    }
                    balanceListMap.put(ByteBuffer.wrap(retentionEdgeList.getHash()), retentionEdgeList);
                    balanceListMap.put(ByteBuffer.wrap(frozenEdgeList.getHash()), frozenEdgeList);

                    // TODO: remove this; it is for debugging only
                    BalanceListManager.genesisList = genesisList;
                    BalanceListManager.retentionEdgeList = retentionEdgeList;
                    BalanceListManager.frozenEdgeList = frozenEdgeList;
                }
            }
        }
    }

    // TODO: remove this; it is for debugging only
    public static synchronized String mapInformation() {

        return balanceListMap.size() + "(G=" + (genesisList == null ? "-" : genesisList.getBlockHeight() + ",r=" +
                (retentionEdgeList == null ? "-" : retentionEdgeList.getBlockHeight()) + ",f=" +
                (frozenEdgeList == null ? "-" : frozenEdgeList.getBlockHeight()) + ")");
    }

    // TODO: remove this; it is for debugging only
    public static long averageWork() {

        return totalWork / Math.max(totalQueries, 1L);
    }
}
