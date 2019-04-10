package co.nyzo.verifier;

import co.nyzo.verifier.util.NotificationUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BalanceListManager {

    private static long totalQueries = 0;
    private static long totalWork = 0;

    // TODO: remove these; they are for debugging only
    private static BalanceList genesisList = null;
    private static BalanceList retentionEdgeList = null;
    private static BalanceList frozenEdgeList = null;

    private static long accountSetHeight = -1L;
    private static Set<ByteBuffer> accountsInSystem = ConcurrentHashMap.newKeySet();

    private static final long maximumMapSize = 6;

    // This is a map from balance list hash to balance list.
    private static final Map<ByteBuffer, BalanceList> balanceListMap = new HashMap<>();

    public static synchronized BalanceList balanceListForBlock(Block block, StringBuilder nullReason) {

        if (nullReason == null) {
            nullReason = new StringBuilder();
        }

        // Only proceed if the block is at or past the retention window start height or is the Genesis block.
        BalanceList balanceList = null;
        if (block != null && (block.getBlockHeight() >= BlockManager.getRetentionEdgeHeight() ||
                block.getBlockHeight() == 0)) {

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
                    List<Block> blocks = new ArrayList<>(Collections.singletonList(startBlock));
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
            if (block == null) {
                nullReason.append("block is null");
            } else {
                nullReason.append("block height is ").append(block.getBlockHeight())
                        .append(" and retention edge height is ").append(BlockManager.getRetentionEdgeHeight());
                NotificationUtil.send("trying to get balance list for height " + block.getBlockHeight() +
                        " when retention edge height is " + BlockManager.getRetentionEdgeHeight() + " on " +
                        Verifier.getNickname());
            }
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

            // If this is a higher edge under the frozen edge than we have previously used to generate the account set,
            // update the accounts in the system.
            if (balanceList.getBlockHeight() > accountSetHeight && balanceList.getBlockHeight() <=
                    BlockManager.getFrozenEdgeHeight()) {

                accountSetHeight = balanceList.getBlockHeight();
                Set<ByteBuffer> accountsInSystem = ConcurrentHashMap.newKeySet();
                for (BalanceListItem item : balanceList.getItems()) {
                    accountsInSystem.add(ByteBuffer.wrap(item.getIdentifier()));
                }

                BalanceListManager.accountsInSystem = accountsInSystem;
            }
        }
    }

    public static boolean accountIsInSystem(byte[] identifier) {

        return accountsInSystem.contains(ByteBuffer.wrap(identifier));
    }

    public static synchronized boolean cleanMap(Block retentionEdge, Block frozenEdge) {

        BalanceList retentionEdgeList = balanceListForBlock(retentionEdge, null);
        BalanceList frozenEdgeList = balanceListForBlock(frozenEdge, null);
        boolean successful = false;
        if (retentionEdgeList != null && frozenEdgeList != null) {

            successful = true;
            BalanceList genesisList = null;
            Block genesisBlock = BlockManager.frozenBlockForHeight(0L);
            if (genesisBlock != null) {
                genesisList = balanceListForBlock(genesisBlock, null);
            }

            BalanceListManager.genesisList = genesisList;
            BalanceListManager.retentionEdgeList = retentionEdgeList;
            BalanceListManager.frozenEdgeList = frozenEdgeList;

            // Clear the map and register the balance lists.
            balanceListMap.clear();
            registerBalanceList(genesisList);
            registerBalanceList(retentionEdgeList);
            registerBalanceList(frozenEdgeList);
        }

        return successful;
    }

    // TODO: remove this; it is for debugging only
    public static synchronized String mapInformation() {

        String genesisString = "G=" + (genesisList == null ? "-" : genesisList.getBlockHeight());
        String retentionString = "r=" + (retentionEdgeList == null || frozenEdgeList == null ? "-" :
                "f-" + (frozenEdgeList.getBlockHeight() - retentionEdgeList.getBlockHeight()));
        String frozenString = "f=" + (frozenEdgeList == null ? "-" : frozenEdgeList.getBlockHeight());
        return balanceListMap.size() + "(" + genesisString + "," + retentionString + "," + frozenString + ")";
    }

    // TODO: remove this; it is for debugging only
    public static long averageWork() {

        return totalWork / Math.max(totalQueries, 1L);
    }
}
