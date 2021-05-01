package co.nyzo.verifier;


import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import co.nyzo.verifier.util.*;  // egg

public class BalanceListManager {

    private static BalanceList genesisList = null;
    private static final int numberOfRecentLists = 4;
    private static BalanceList[] recentLists = new BalanceList[numberOfRecentLists];
    private static Map<ByteBuffer, Long> frozenEdgeBalanceMap = new ConcurrentHashMap<>();

    private static Set<ByteBuffer> accountsInSystem = ConcurrentHashMap.newKeySet();

    private static final long maximumMapSize = 6;

    // This is a map from balance list hash to balance list.
    private static final Map<ByteBuffer, BalanceList> balanceListMap = new ConcurrentHashMap<>();

    public static BalanceList recentBalanceListForHeight(long blockHeight) {

        // This method is not synchronized. So, store references to the list to avoid the situation where a
        // particular list is checked and then the array slot is reassigned before the reference is assigned to
        // the result variable.
        BalanceList[] recentLists = new BalanceList[numberOfRecentLists];
        System.arraycopy(BalanceListManager.recentLists, 0, recentLists, 0, numberOfRecentLists);

        // We only need check the height, as these are frozen blocks.
        BalanceList result = null;
        for (int i = 0; i < numberOfRecentLists && result == null; i++) {
            if (recentLists[i] != null && recentLists[i].getBlockHeight() == blockHeight) {
                result = recentLists[i];
            }
        }

        return result;
    }

    public static BalanceList balanceListForBlock(Block block) {

        BalanceList balanceList = null;
        if (block != null) {

            // Get a local reference to the list at the frozen edge. The array is ordered by decreasing block height.
            BalanceList frozenEdgeList = recentLists[0];

            BalanceList recentList = recentBalanceListForHeight(block.getBlockHeight());
            if (block.getBlockHeight() == 0) {
                if (genesisList == null) {
                    genesisList = Block.balanceListForNextBlock(null, null, block.getTransactions(),
                            block.getVerifierIdentifier(), block.getBlockchainVersion());
                }
                balanceList = genesisList;
            } else if (recentList != null) {
                balanceList = recentList;
            } else if (frozenEdgeList == null || block.getBlockHeight() == frozenEdgeList.getBlockHeight() + 1) {

                // First, try to get the balance list from the map.
                balanceList = balanceListMap.get(ByteBuffer.wrap(block.getBalanceListHash()));

                // If the balance list was not in the map, try to derive it.
                if (balanceList == null) {
                    //System.out.println("X:BLM:Rewind from "+block.getBlockHeight()+ " " + block);
                    /*
                    for (ByteBuffer key: balanceListMap.keySet()) {
                        System.out.println("X:BLM:hash="+PrintUtil.compactPrintByteArray(key.array())+" h="+balanceListMap.get(key).getBlockHeight() );
                    }*/

                    // Step back to previous blocks until we are able to find a balance list that we have.
                    Block startBlock = block;
                    List<Block> blocks = new ArrayList<>(Collections.singletonList(startBlock));
                    BalanceList startBalanceList = null;
                    if (balanceListMap.size() > 0) {
                        long rewinded = 0L;
                        while (startBlock != null && startBalanceList == null) {
                            startBlock = startBlock.getPreviousBlock();
                            rewinded++;
                            if (startBlock != null) {
                                blocks.add(0, startBlock);
                                // System.out.println("X:BLM:Previous is "+startBlock.getBlockHeight());
                                startBalanceList = balanceListMap.get(ByteBuffer.wrap(startBlock.getBalanceListHash()));
                            } else {
                                //System.out.println("X:BLM:Previous was null");
                            }
                        }
                        if (startBlock != null) {
                            //System.out.println("X:BLM:Rewinded to "+startBlock);
                        } else {
                            System.out.println("X:BLM:Rewinded to null after " + rewinded);
                        }
                    } else {
                        System.out.println("X:BLM:!Can't rewind, No BL at all");
                    }
                    // If a suitable start balance list was found, derive the desired list.
                    if (startBalanceList != null) {
                        balanceList = startBalanceList;
                        for (int i = 0; i < blocks.size() - 1; i++) {
                            //System.out.println("X:BLM:Adding block "+blocks.get(i) );
                            //System.out.println("X:BLM:and block "+blocks.get(i+1) );
                            balanceList = Block.balanceListForNextBlock(blocks.get(i), balanceList,
                                    blocks.get(i + 1).getTransactions(), blocks.get(i + 1).getVerifierIdentifier(),
                                    blocks.get(i + 1).getBlockchainVersion());
                        }
                        //System.out.println("X:Resulting BL Hash="+PrintUtil.compactPrintByteArray(balanceList.getHash()) );
                        registerBalanceList(balanceList);
                    } else {
                        System.out.println("X:BLM:Rewinded BL is null");
                    }
                } else {
                    //System.out.println("X:BLM:BL From map");
                }
            }
        } else {
            System.out.println("X:BLM:Block is null");
        }

        return balanceList;
    }

    public static void registerBalanceList(BalanceList balanceList) {
        if (balanceList == null) {
            System.out.println("X:BLM:Not registering null BL");
        } else if (balanceListMap.size() < maximumMapSize) {
            balanceListMap.put(ByteBuffer.wrap(balanceList.getHash()), balanceList);
        } else {
            System.out.println("X:BLM:Not registering BL, mapsize exceeded");
        }
    }

    public static BalanceList getFrozenEdgeList() {
        return recentLists[0];
    }

    public static Map<ByteBuffer, Long> getFrozenEdgeBalanceMap() {
        return frozenEdgeBalanceMap;
    }

    public static boolean accountIsInSystem(byte[] identifier) {

        return accountsInSystem.contains(ByteBuffer.wrap(identifier));
    }

    public static void updateFrozenEdge(BalanceList frozenEdgeList) {

        if (frozenEdgeList != null) {
            try {
                for (int i = numberOfRecentLists - 1; i > 0; i--) {
                    recentLists[i] = recentLists[i - 1];
                }
                recentLists[0] = frozenEdgeList;
                frozenEdgeBalanceMap = BalanceManager.makeBalanceMap(frozenEdgeList);

                Set<ByteBuffer> accountsInSystem = ConcurrentHashMap.newKeySet();
                for (BalanceListItem item : frozenEdgeList.getItems()) {
                    accountsInSystem.add(ByteBuffer.wrap(item.getIdentifier()));
                }

                BalanceListManager.accountsInSystem = accountsInSystem;

                balanceListMap.clear();
                //System.out.println("X:BLM:balanceListMap.cleared, add "+ PrintUtil.compactPrintByteArray(frozenEdgeList.getHash())+" for h="+frozenEdgeList.getBlockHeight());
                balanceListMap.put(ByteBuffer.wrap(frozenEdgeList.getHash()), frozenEdgeList);
            } catch (Exception e) {
                System.out.println("X:Exception BLM.updateFrozenEdge " + PrintUtil.printException(e));
            }
        } else {
            System.out.println("X:BLM:updateFrozenEdge BL is null");
        }
    }
}
