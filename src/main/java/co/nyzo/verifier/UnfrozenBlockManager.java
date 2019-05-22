package co.nyzo.verifier;

import co.nyzo.verifier.messages.BlockVote;
import co.nyzo.verifier.messages.MissingBlockRequest;
import co.nyzo.verifier.messages.MissingBlockResponse;
import co.nyzo.verifier.util.NotificationUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class UnfrozenBlockManager {

    private static Map<Long, Map<ByteBuffer, Block>> unfrozenBlocks = new ConcurrentHashMap<>();
    private static Map<Long, Integer> thresholdOverrides = new ConcurrentHashMap<>();
    private static Map<Long, byte[]> hashOverrides = new ConcurrentHashMap<>();

    private static String voteDescription = "*** not yet voted ***";

    private static Map<Long, Map<ByteBuffer, Block>> disconnectedBlocks = new ConcurrentHashMap<>();

    private static long lastBlockVoteTimestamp = 0L;

    public static void attemptToRegisterDisconnectedBlocks() {

        // Remove the disconnected blocks one past the frozen edge from the disconnected map. Attempt to register them.
        long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
        Map<ByteBuffer, Block> disconnectedBlocksForHeight = disconnectedBlocks.remove(frozenEdgeHeight + 1);
        if (disconnectedBlocksForHeight != null) {
            for (Block block : disconnectedBlocksForHeight.values()) {
                registerBlock(block);
            }
        }
    }

    public static boolean registerBlock(Block block) {

        boolean registeredBlock = false;

        // Reject all blocks with invalid signatures. We should only be working one past the frozen edge, but we will
        // accept to the open edge in case we have gotten behind.
        long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
        if (block != null && block.getBlockHeight() > frozenEdgeHeight && block.signatureIsValid() &&
                block.getBlockHeight() <= BlockManager.openEdgeHeight(true)) {

            // Get the map of blocks at this height.
            Map<ByteBuffer, Block> blocksAtHeight = unfrozenBlocks.get(block.getBlockHeight());
            if (blocksAtHeight == null) {
                blocksAtHeight = new HashMap<>();
                unfrozenBlocks.put(block.getBlockHeight(), blocksAtHeight);
            }

            // Check if the block is a simple duplicate (same hash).
            boolean alreadyContainsBlock = blocksAtHeight.containsKey(ByteBuffer.wrap(block.getHash()));

            // Check if the block has a valid verification timestamp. We cannot be sure of this, but we can filter out
            // some invalid blocks at this point.
            boolean verificationTimestampValid = true;
            if (!alreadyContainsBlock) {

                // Check that the interval is not less than the minimum.
                Block previousBlock = block.getPreviousBlock();
                if (previousBlock != null && previousBlock.getVerificationTimestamp() >
                        block.getVerificationTimestamp() - Block.minimumVerificationInterval) {
                    verificationTimestampValid = false;
                }

                // Check that the verification timestamp is not unreasonably far into the future.
                if (block.getVerificationTimestamp() > System.currentTimeMillis() + 5000L) {
                    verificationTimestampValid = false;
                }
            }

            if (!alreadyContainsBlock && verificationTimestampValid) {

                // At this point, it is prudent to independently calculate the balance list. We only register the block
                // if we can calculate the balance list and if the hash matches what we expect. This will ensure that no
                // blocks with invalid transactions are registered (they will be removed in the balance-list
                // calculation, and the hash will not match).
                BalanceList balanceList = BalanceListManager.balanceListForBlock(block);
                if (balanceList != null && ByteUtil.arraysAreEqual(balanceList.getHash(), block.getBalanceListHash())) {

                    blocksAtHeight.put(ByteBuffer.wrap(block.getHash()), block);
                    registeredBlock = true;

                    // Only keep the best 500 blocks at any level. For stability in the list, consider the just-added
                    // block to be the highest-scored, and only remove another block if it has a higher score than the
                    // new block.
                    if (blocksAtHeight.size() > 500 && !BlockManager.inGenesisCycle()) {
                        Block highestScoredBlock = block;
                        long highestScore = highestScoredBlock.chainScore(frozenEdgeHeight);
                        for (Block blockAtHeight : blocksAtHeight.values()) {
                            long score = blockAtHeight.chainScore(frozenEdgeHeight);
                            if (score > highestScore) {
                                highestScore = score;
                                highestScoredBlock = blockAtHeight;
                            }
                        }

                        blocksAtHeight.remove(ByteBuffer.wrap(highestScoredBlock.getHash()));
                    }
                } else if (balanceList == null && block.getBlockHeight() > frozenEdgeHeight + 1) {

                    // This is a special case when we have fallen behind the frozen edge. We may get a block for which
                    // the balance list is currently null, but it might not be null later. So, we should save it for now
                    // to avoid having to request it later.
                    Map<ByteBuffer, Block> disconnectedBlocksForHeight = disconnectedBlocks.get(block.getBlockHeight());
                    if (disconnectedBlocksForHeight == null) {
                        disconnectedBlocksForHeight = new HashMap<>();
                        disconnectedBlocks.put(block.getBlockHeight(), disconnectedBlocksForHeight);
                    }

                    disconnectedBlocksForHeight.put(ByteBuffer.wrap(block.getHash()), block);
                }
            }
        }

        return registeredBlock;
    }

    public static void updateVote() {

        // Only vote for the first height past the frozen edge, and only continue if we have blocks and have not voted
        // for this height in less than the minimum interval time (the additional 200ms is to account for network
        // jitter and other minor time variations).
        long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
        long height = frozenEdgeHeight + 1;
        Map<ByteBuffer, Block> blocksForHeight = unfrozenBlocks.get(height);
        if (blocksForHeight != null && !blocksForHeight.isEmpty() &&
                lastBlockVoteTimestamp < System.currentTimeMillis() - BlockVoteManager.minimumVoteInterval - 200L) {

            // This will be the vote that we determine based on the current state. Previously, we would only broadcast
            // changed votes to the cycle. Now, we broadcast all votes to the cycle, as the new flip-vote mechanism
            // requires multiple broadcasts.
            byte[] newVoteHash = null;

            String voteDescription;
            byte[] hashOverride = hashOverrides.get(height);
            if (hashOverride != null) {

                // We always use an override if one is available.
                newVoteHash = hashOverride;

                voteDescription = "override; ";

            } else if (BlockManager.inGenesisCycle()) {

                voteDescription = "Genesis cycle; ";

                // In the Genesis cycle, we always vote for the lowest score available at any time.
                Block lowestScoredBlock = null;
                long lowestChainScore = Long.MAX_VALUE;
                for (Block block : blocksForHeight.values()) {
                    long blockChainScore = block.chainScore(frozenEdgeHeight);
                    if (lowestScoredBlock == null || blockChainScore < lowestChainScore) {
                        lowestChainScore = blockChainScore;
                        lowestScoredBlock = block;
                    }
                }
                System.out.println("(Genesis) lowest-scored block: " + lowestScoredBlock + ", score: " +
                        lowestChainScore);

                if (lowestScoredBlock != null) {
                    newVoteHash = lowestScoredBlock.getHash();
                }

            } else {
                Block newVoteBlock = null;

                // Get the current votes for this height. If a block has greater than 50% of the vote, vote for it
                // if its score allows voting yet. Otherwise, if the leading hash has a score that allowed voting more
                // than 10 seconds ago, vote for it even if it does not exceed 50%. This allows us to reach consensus
                // even if no hash exceeds 50%. We do not try to agree with the rest of the cycle until we receive at
                // least 75% of the vote for the height.
                int votingPoolSize = BlockManager.currentCycleLength();
                int numberOfVotesAtHeight = BlockVoteManager.numberOfVotesAtHeight(height);
                if (numberOfVotesAtHeight > votingPoolSize * 3 / 4) {
                    AtomicInteger voteCountWrapper = new AtomicInteger(0);
                    byte[] leadingHash = BlockVoteManager.leadingHashForHeight(height, voteCountWrapper);
                    Block leadingHashBlock = unfrozenBlockAtHeight(height, leadingHash);
                    if (leadingHashBlock != null) {
                        int voteCount = voteCountWrapper.get();
                        if ((voteCount > votingPoolSize / 2 && leadingHashBlock.getMinimumVoteTimestamp() <=
                                System.currentTimeMillis()) ||
                                leadingHashBlock.getMinimumVoteTimestamp() < System.currentTimeMillis() - 10000L) {
                            newVoteBlock = leadingHashBlock;
                            voteDescription = "leading; ";
                        } else {
                            voteDescription = "insufficient leading score; ";
                        }
                    } else {
                        voteDescription = "missing leading; ";
                    }
                } else {
                    voteDescription = "insufficient count=" + numberOfVotesAtHeight + "; ";
                }

                // If we did not determine a vote to agree with the rest of the mesh, then we independently choose the
                // block that we think is best.
                if (newVoteBlock == null) {

                    // Find the block with the lowest score at this height.
                    Block lowestScoredBlock = null;
                    long lowestChainScore = Long.MAX_VALUE;
                    for (Block block : blocksForHeight.values()) {
                        long blockChainScore = block.chainScore(frozenEdgeHeight);
                        if (lowestScoredBlock == null || blockChainScore < lowestChainScore) {
                            lowestChainScore = blockChainScore;
                            lowestScoredBlock = block;
                        }
                    }

                    if (lowestScoredBlock != null &&
                            lowestScoredBlock.getMinimumVoteTimestamp() <= System.currentTimeMillis()) {

                        newVoteBlock = lowestScoredBlock;
                        voteDescription += "lowest-scored; ";
                    }
                }

                if (newVoteBlock != null) {
                    newVoteHash = newVoteBlock.getHash();
                    voteDescription += "h=" + height + "; " + PrintUtil.compactPrintByteArray(newVoteHash);
                } else {
                    voteDescription += "h=" + height + "; undetermined";
                }
            }

            UnfrozenBlockManager.voteDescription = voteDescription;

            // If we determined a vote, broadcast it to the cycle.
            if (newVoteHash != null) {
                castVote(height, newVoteHash);
            }
        }
    }

    public static void castVote(long height, byte[] hash) {

        System.out.println("^^^^^^^^^^^^^^^^^^^^^ casting vote for height " + height);
        lastBlockVoteTimestamp = System.currentTimeMillis();

        // Create the vote and register it locally.
        BlockVote vote = new BlockVote(height, hash, System.currentTimeMillis());
        Message message = new Message(MessageType.BlockVote19, vote);
        BlockVoteManager.registerVote(message);

        // Send the vote if this verifier is in the cycle or if this is the Genesis cycle.
        if (Verifier.inCycle() || BlockManager.inGenesisCycle()) {
            Message.broadcast(message);
        }
    }

    public static boolean attemptToFreezeBlock() {

        long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
        long heightToFreeze = frozenEdgeHeight + 1;

        // Get the vote tally for the height we are trying to freeze.
        AtomicInteger voteCountWrapper = new AtomicInteger(0);
        byte[] leadingHash = BlockVoteManager.leadingHashForHeight(heightToFreeze, voteCountWrapper);
        int voteCount = voteCountWrapper.get();

        // If the vote count is greater than 75% of the voting pool, freeze the block. Previously, there was a delay
        // and a second check here, but it will no longer have any effect due to the new flip-vote mechanism.
        int votingPoolSize = BlockManager.inGenesisCycle() ? NodeManager.getMeshSize() :
                BlockManager.currentCycleLength();
        int voteCountThreshold = thresholdOverrides.containsKey(heightToFreeze) ?
                votingPoolSize * thresholdOverrides.get(heightToFreeze) / 100 :
                votingPoolSize * 3 / 4;
        boolean frozeBlock = false;
        if (voteCount > voteCountThreshold) {

            Block block = unfrozenBlockAtHeight(heightToFreeze, leadingHash);
            if (block != null) {
                System.out.println("freezing block " + block + " with standard mechanism");
                BlockManager.freezeBlock(block);
                frozeBlock = true;
            }
        }

        return frozeBlock;
    }

    public static void attemptToFreezeChain() {

        // The logic to freeze a section of the chain is different. This only happens in a situation where this
        // verifier has had problems tracking the chain and is trying to catch up. Only the 75% threshold is used, and
        // it must be surpassed for two consecutive blocks. If it is surpassed for two consecutive blocks, and if all
        // of the blocks from the second passing block all the way back to the frozen edge are available, then the
        // entire chain to the first of the two consecutive blocks is frozen.

        // This method freezes the shortest length of chain that it can freeze. As this is a recovery method, the idea
        // is to make some progress while taking as little risk as possible.

        // To avoid unnecessary work, only attempt this process if we have votes for at least five different heights.
        List<Long> voteHeights = BlockVoteManager.getHeights();

        int votingPoolSize = BlockManager.inGenesisCycle() ? NodeManager.getMeshSize() :
                BlockManager.currentCycleLength();
        int voteCountThreshold = votingPoolSize * 3 / 4;

        long firstPassingHeight = -1L;
        byte[] firstPassingHash = null;
        boolean foundSecondPassingHeight = false;
        long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
        for (int i = 0; i < voteHeights.size() && !foundSecondPassingHeight; i++) {

            long height = voteHeights.get(i);
            if (height > frozenEdgeHeight + 1) {
                AtomicInteger voteCount = new AtomicInteger(0);
                byte[] leadingHash = BlockVoteManager.leadingHashForHeight(height, voteCount);
                if (voteCount.get() > voteCountThreshold) {

                    // If this is the second consecutive block that qualifies for a vote, check the previous-block
                    // hash to ensure that the second block is a successor of the first.
                    if (height == firstPassingHeight + 1) {
                        Block blockAtSecondHeight = unverifiedBlockAtHeight(height, leadingHash);
                        if (blockAtSecondHeight != null &&
                                ByteUtil.arraysAreEqual(blockAtSecondHeight.getPreviousBlockHash(),
                                        firstPassingHash)) {
                            foundSecondPassingHeight = true;
                        }
                    }

                    // If a second passing height was not established, the vote count still qualifies this height
                    // as a first passing height.
                    if (!foundSecondPassingHeight) {
                        firstPassingHeight = height;
                        firstPassingHash = leadingHash;
                    }
                }
            }
        }

        // If a second passing height was found, try to freeze the entire section of chain to the first passing
        // height.
        if (foundSecondPassingHeight) {

            List<Block> blocks = new ArrayList<>();
            boolean allBlocksAvailable = true;
            byte[] hash = firstPassingHash;
            for (long height = firstPassingHeight; height > frozenEdgeHeight && allBlocksAvailable; height--) {

                // Try to get the block for this height.
                Block block = unverifiedBlockAtHeight(height, hash);

                // If the block is not available, this section of chain cannot be frozen. If it is available, add
                // it to the beginning of the list and continue stepping toward the frozen edge.
                if (block == null) {
                    allBlocksAvailable = false;
                } else {
                    blocks.add(0, block);
                    hash = block.getPreviousBlockHash();
                }
            }

            if (allBlocksAvailable) {
                for (Block block : blocks) {
                    System.out.println("freezing chain block " + block);
                    BlockManager.freezeBlock(block);
                }
            }
        }

    }

    public static void performMaintenance() {

        // This method is called to clean up after freezing a block.

        // Reset the block vote timestamp. This allows us to vote immediately for the next block if we are catching
        // up.
        lastBlockVoteTimestamp = 0L;

        // Remove blocks at or below the new frozen edge.
        long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
        for (Long height : new HashSet<>(unfrozenBlocks.keySet())) {
            if (height <= frozenEdgeHeight) {
                unfrozenBlocks.remove(height);
            }
        }

        // Remove disconnected blocks at or below the new frozen edge.
        for (long height : new HashSet<>(disconnectedBlocks.keySet())) {
            if (height <= frozenEdgeHeight) {
                disconnectedBlocks.remove(height);
            }
        }

        // Remove threshold overrides at or below the new frozen edge.
        for (Long height : new HashSet<>(thresholdOverrides.keySet())) {
            if (height <= frozenEdgeHeight) {
                thresholdOverrides.remove(height);
            }
        }

        // Remove hash overrides at or below the new frozen edge.
        for (Long height : new HashSet<>(hashOverrides.keySet())) {
            if (height <= frozenEdgeHeight) {
                hashOverrides.remove(height);
            }
        }
    }

    public static void fetchMissingBlock(long height, byte[] hash) {

        NotificationUtil.send("fetching block " + height + " (" + PrintUtil.compactPrintByteArray(hash) +
                ") from mesh on " + Verifier.getNickname());
        Message blockRequest = new Message(MessageType.MissingBlockRequest25,
                new MissingBlockRequest(height, hash));
        Message.fetchFromRandomNode(blockRequest, new MessageCallback() {
            @Override
            public void responseReceived(Message message) {

                MissingBlockResponse response = (MissingBlockResponse) message.getContent();
                Block responseBlock = response.getBlock();
                if (responseBlock != null && ByteUtil.arraysAreEqual(responseBlock.getHash(), hash)) {
                    System.out.println("got block for height " + responseBlock.getBlockHeight());
                    registerBlock(responseBlock);
                }
            }
        });
    }

    public static Set<Long> unfrozenBlockHeights() {

        return new HashSet<>(unfrozenBlocks.keySet());
    }

    public static int numberOfBlocksAtHeight(long height) {

        int number = 0;
        Map<ByteBuffer, Block> blocks = unfrozenBlocks.get(height);
        if (blocks != null) {
            number = blocks.size();
        }

        return number;
    }

    public static List<Block> allUnfrozenBlocks() {

        List<Block> allBlocks = new ArrayList<>();
        for (Map<ByteBuffer, Block> blocks : unfrozenBlocks.values()) {
            allBlocks.addAll(blocks.values());
        }

        return allBlocks;
    }

    public static List<Block> unfrozenBlocksAtHeight(long height) {

        Map<ByteBuffer, Block> mapForHeight = unfrozenBlocks.get(height);
        return mapForHeight == null ? new ArrayList<>() : new ArrayList<>(mapForHeight.values());
    }

    public static Block unfrozenBlockAtHeight(long height, byte[] hash) {

        return blockAtHeight(height, hash, unfrozenBlocks);
    }

    public static Block unverifiedBlockAtHeight(long height, byte[] hash) {

        // If the unfrozen block is not available, look to the disconnected block map. These blocks, importantly, have
        // not passed the same level of local vetting at the unfrozen blocks, as we have not yet been able to
        // independently calculate their balance lists. Care must be exercised to only use this method in situations
        // where this verifier has fallen behind the cycle and the cycle has already reached consensus on the block
        // in question.

        Block block = blockAtHeight(height, hash, unfrozenBlocks);
        if (block == null) {
            block = blockAtHeight(height, hash, disconnectedBlocks);
        }

        return block;
    }

    private static Block blockAtHeight(long height, byte[] hash, Map<Long, Map<ByteBuffer, Block>> blockMap) {

        Block block = null;
        if (hash != null) {
            Map<ByteBuffer, Block> blocksAtHeight = blockMap.get(height);
            if (blocksAtHeight != null) {
                block = blocksAtHeight.get(ByteBuffer.wrap(hash));
            }
        }

        return block;
    }

    public static void purge() {

        unfrozenBlocks.clear();
    }

    public static void requestMissingBlocks() {

        long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
        for (long height : BlockVoteManager.getHeights()) {
            if (height == frozenEdgeHeight + 1) {
                for (ByteBuffer hash : BlockVoteManager.getHashesForHeight(height)) {
                    Block block = unfrozenBlockAtHeight(height, hash.array());
                    if (block == null) {
                        fetchMissingBlock(height, hash.array());
                    }
                }
            }
        }
    }

    public static void setThresholdOverride(long height, int threshold) {

        if (threshold == 0) {
            thresholdOverrides.remove(height);
        } else if (threshold < 100) {
            thresholdOverrides.put(height, threshold);
        }
    }

    public static void setHashOverride(long height, byte[] hash) {

        if (ByteUtil.isAllZeros(hash)) {
            hashOverrides.remove(height);
        } else {
            hashOverrides.put(height, hash);
        }
    }

    public static Map<Long, Integer> getThresholdOverrides() {

        return thresholdOverrides;
    }

    public static Map<Long, byte[]> getHashOverrides() {

        return hashOverrides;
    }

    public static String getVoteDescription() {

        return voteDescription;
    }
}
