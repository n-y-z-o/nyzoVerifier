package co.nyzo.verifier;

import co.nyzo.verifier.messages.BlockRequest;
import co.nyzo.verifier.messages.BlockResponse;
import co.nyzo.verifier.messages.BootstrapResponse;
import co.nyzo.verifier.util.DebugUtil;
import co.nyzo.verifier.util.NotificationUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ChainInitializationManager {

    private static final Map<Long, FrozenBlockVoteTally> hashVotes = new HashMap<>();

    public static synchronized void processBootstrapResponseMessage(Message message) {

        BootstrapResponse response = (BootstrapResponse) message.getContent();

        // Accumulate votes for the hashes.
        int numberOfHashes = response.getFrozenBlockHashes().size();
        for (int i = 0; i < numberOfHashes; i++) {
            long blockHeight = response.getFirstHashHeight() + i;
            FrozenBlockVoteTally voteTally = hashVotes.get(blockHeight);
            if (voteTally == null) {
                voteTally = new FrozenBlockVoteTally(blockHeight);
                hashVotes.put(blockHeight, voteTally);
            }

            byte[] hash = response.getFrozenBlockHashes().get(i);
            long startHeight = response.getStartHeights().get(i);
            voteTally.vote(message.getSourceNodeIdentifier(), hash, startHeight);
        }
    }

    public static synchronized long frozenEdgeHeight(byte[] frozenEdgeHash, AtomicLong frozenEdgeStartHeight) {

        // Determine the maximum number of votes we have at any level. This determines our consensus threshold.
        int maximumVotesAtAnyLevel = 0;
        for (FrozenBlockVoteTally tally : hashVotes.values()) {
            maximumVotesAtAnyLevel = Math.max(maximumVotesAtAnyLevel, tally.totalVotes());
        }

        // Determine the highest level at which consensus has been reached.
        long maximumConsensusHeight = -1;
        byte[] winnerHash = new byte[FieldByteSize.hash];
        if (maximumVotesAtAnyLevel > 0) {
            for (long height : hashVotes.keySet()) {
                if (height > maximumConsensusHeight) {
                    FrozenBlockVoteTally tally = hashVotes.get(height);
                    AtomicLong startHeight = new AtomicLong();
                    if (tally.votesForWinner(winnerHash, startHeight) > maximumVotesAtAnyLevel / 2) {
                        maximumConsensusHeight = height;
                        System.arraycopy(winnerHash, 0, frozenEdgeHash, 0, FieldByteSize.hash);
                        frozenEdgeStartHeight.set(startHeight.get());
                    }
                }
            }
        }

        return maximumConsensusHeight;
    }

    public static void fetchChainSection(long startHeight, long endHeight, byte[] endBlockHash) {

        // Only fetch the balance list if the section does not connect to previously frozen blocks.
        long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
        boolean requireBalanceList = startHeight > frozenEdgeHeight + 1;

        Map<Long, Block> blocksToSave = new HashMap<>();
        int numberOfBlocksRequired = (int) (endHeight - startHeight + 1L);
        Set<BalanceList> initialBalanceList = new HashSet<>();  // single item; using a set to allow access from thread
        boolean missingBlocks = true;
        boolean missingBalanceList = requireBalanceList;
        while ((missingBlocks || missingBalanceList) && !UpdateUtil.shouldTerminate()) {

            // The chain is built from the end to the beginning so hashes can be confirmed.
            long minimumHeightAlreadyFetched = endHeight + 1;
            for (Long height : blocksToSave.keySet()) {
                minimumHeightAlreadyFetched = Math.min(minimumHeightAlreadyFetched, height);
            }
            long requestEndHeight = Math.max(startHeight, Math.min(endHeight, minimumHeightAlreadyFetched - 1));

            System.out.println("fetching from height " + startHeight + " to " + requestEndHeight);

            Message message = new Message(MessageType.BlockRequest11, new BlockRequest(startHeight, requestEndHeight,
                    requireBalanceList));
            Message.fetch(message, new MessageCallback() {
                @Override
                public void responseReceived(Message message) {

                    BlockResponse response = (BlockResponse) message.getContent();
                    Map<Long, Block> responseBlocks = blockMap(response.getBlocks());
                    long height = requestEndHeight;
                    boolean addedAllAvailable = false;
                    while (!addedAllAvailable) {

                        Block block = responseBlocks.get(height);
                        if (block != null) {
                            byte[] requiredHash = height == endHeight ? endBlockHash :
                                    blocksToSave.get(height + 1).getPreviousBlockHash();
                            if (!ByteUtil.arraysAreEqual(block.getHash(), requiredHash)) {
                                System.out.println("discarded block at height " + height + " due to incorrect hash, " +
                                        "hash=" + PrintUtil.compactPrintByteArray(block.getHash()) + ", required=" +
                                        PrintUtil.compactPrintByteArray(requiredHash));
                                block = null;
                            }
                        }

                        if (block == null) {
                            addedAllAvailable = true;
                        } else {
                            blocksToSave.put(height, block);
                            System.out.println("added block at height " + height);
                            height--;
                        }
                    }

                    if (response.getInitialBalanceList() != null &&
                            response.getInitialBalanceList().getBlockHeight() == startHeight) {
                        initialBalanceList.add(response.getInitialBalanceList());
                    }
                }
            });

            missingBlocks = blocksToSave.size() < numberOfBlocksRequired;
            missingBalanceList = requireBalanceList && initialBalanceList.isEmpty();
            if ((missingBlocks || missingBalanceList) && !UpdateUtil.shouldTerminate()) {
                try {
                    Thread.sleep(2000);
                } catch (Exception ignored) {
                }
            }
        }

        if (!missingBlocks && !missingBalanceList) {

            BalanceList balanceList = initialBalanceList.isEmpty() ? null :
                    initialBalanceList.iterator().next();
            if (balanceList != null) {
                BalanceListManager.registerBalanceList(balanceList);
            }

            // Save the blocks.
            for (long height = startHeight; height <= endHeight; height++) {
                Block block = blocksToSave.get(height);
                if (height == startHeight && startHeight > frozenEdgeHeight + 1) {
                    BlockManager.freezeBlock(block, block.getPreviousBlockHash(), balanceList);
                } else {
                    BlockManager.freezeBlock(block);
                }
            }

            // Now that the blocks are saved, we should be able to determine the continuity state of the end block.
            try {
                Block endBlock = BlockManager.frozenBlockForHeight(endHeight);
                System.out.println("end block (" + endBlock.getBlockHeight() + ") continuity state: " +
                        endBlock.getContinuityState() + ", cycle length: " +
                        endBlock.getCycleInformation().getCycleLength());
            } catch (Exception reportOnly) {
                NotificationUtil.send("unable to determine continuity state on " + Verifier.getNickname() +
                        " at end of chain initialization: " + DebugUtil.callingMethods(8));
            }
        }
    }

    private static Map<Long, Block> blockMap(List<Block> blocks) {

        Map<Long, Block> map = new HashMap<>();
        for (Block block : blocks) {
            map.put(block.getBlockHeight(), block);
        }

        return map;
    }
}
