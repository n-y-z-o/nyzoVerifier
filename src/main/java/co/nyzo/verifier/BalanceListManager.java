package co.nyzo.verifier;

import co.nyzo.verifier.util.NotificationUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class BalanceListManager {

    private static final long maximumMapSize = 5;

    // This is a map from balance list hash to balance list.
    private static final Map<ByteBuffer, BalanceList> balanceListMap = new HashMap<>();

    public static synchronized BalanceList balanceListForBlock(Block block) {

        // Only proceed if the block is at or past the frozen edge.
        BalanceList balanceList = null;
        if (block.getBlockHeight() >= BlockManager.getFrozenEdgeHeight()) {

            // First, try to get the balance list from the map.
            balanceList = balanceListMap.get(ByteBuffer.wrap(block.getBalanceListHash()));

            // If the balance list was not in the map, try to derive it.
            if (balanceList == null) {

                if (block.getBlockHeight() == 0L) {  // special case for Genesis block

                    balanceList = Block.balanceListForNextBlock(null, null, block.getTransactions(),
                            block.getVerifierIdentifier());
                    registerBalanceList(balanceList);

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

                        // TODO: remove this notification
                        StringBuilder availableHeights = new StringBuilder();
                        String separator = "";
                        for (BalanceList cachedBalanceList : balanceListMap.values()) {
                            availableHeights.append(separator).append(cachedBalanceList.getBlockHeight());
                            separator = ",";
                        }
                        if (blocks.size() > 40) {
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
                    }
                }
            }
        } else {
            NotificationUtil.send("trying to get balance list for height " + block.getBlockHeight() + " when frozen " +
                    "edge height is " + BlockManager.getFrozenEdgeHeight() + " on " + Verifier.getNickname());
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

            // If the map is too large, remove less-used entries, keeping the frozen edge and the trailing edge from
            // the block manager.
            if (balanceListMap.size() > maximumMapSize) {

                // Find the balance lists for the frozen and trailing edges.
                long trailingEdgeHeight = BlockManager.getTrailingEdgeHeight();
                long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
                BalanceList trailingEdgeList = null;
                BalanceList frozenEdgeList = null;
                for (BalanceList item : balanceListMap.values()) {
                    long itemHeight = item.getBlockHeight();

                    // Find the highest balance list at or before the trailing edge. This is necessary for
                    // bootstrapping new verifiers.
                    if (itemHeight <= trailingEdgeHeight &&
                            (trailingEdgeList == null || itemHeight > trailingEdgeList.getBlockHeight())) {
                        trailingEdgeList = item;
                    }

                    // Find the highest balance list at or before the frozen edge. This is necessary for creating new
                    // blocks.
                    if (itemHeight <= frozenEdgeHeight &&
                            (frozenEdgeList == null || itemHeight > frozenEdgeList.getBlockHeight())) {
                        frozenEdgeList = item;
                    }
                }

                if (trailingEdgeList != null && frozenEdgeList != null) {
                    balanceListMap.clear();
                    balanceListMap.put(ByteBuffer.wrap(trailingEdgeList.getHash()), trailingEdgeList);
                    balanceListMap.put(ByteBuffer.wrap(frozenEdgeList.getHash()), frozenEdgeList);
                }
            }
        }
    }

    public static int mapSize() {

        return balanceListMap.size();
    }
}
