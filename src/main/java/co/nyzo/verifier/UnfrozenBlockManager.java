package co.nyzo.verifier;

import co.nyzo.verifier.messages.BlockRequest;
import co.nyzo.verifier.messages.BlockVote;
import co.nyzo.verifier.messages.MissingBlockRequest;
import co.nyzo.verifier.messages.MissingBlockResponse;
import co.nyzo.verifier.util.NotificationUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class UnfrozenBlockManager {

    private static Set<Long> votesCast = new HashSet<>();
    private static Map<Long, Map<ByteBuffer, Block>> unfrozenBlocks = new HashMap<>();

    public static synchronized boolean registerBlock(Block block) {

        boolean registeredBlock = false;

        // Reject all blocks with invalid signatures and all those at or behind the frozen edge or ahead of the open
        // edge.
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

            // Check if the block is a duplicate verifier on the same previous block.
            boolean alreadyContainsVerifierOnSameChain = false;
            if (!alreadyContainsBlock) {
                for (Block blockAtHeight : blocksAtHeight.values()) {
                    if (ByteUtil.arraysAreEqual(blockAtHeight.getVerifierIdentifier(), block.getVerifierIdentifier()) &&
                            ByteUtil.arraysAreEqual(blockAtHeight.getPreviousBlockHash(),
                                    block.getPreviousBlockHash())) {
                        alreadyContainsVerifierOnSameChain = true;
                    }
                }
            }

            // Check if the block has a valid verification timestamp. We cannot be sure of this, but we can filter out
            // some invalid blocks at this point.
            boolean verificationTimestampIntervalValid = true;
            if (!alreadyContainsBlock && !alreadyContainsVerifierOnSameChain) {
                Block previousBlock = block.getPreviousBlock();
                if (previousBlock != null && previousBlock.getVerificationTimestamp() >
                        block.getVerificationTimestamp() - Block.minimumVerificationInterval) {
                    verificationTimestampIntervalValid = false;
                }
            }

            if (!alreadyContainsBlock && !alreadyContainsVerifierOnSameChain && verificationTimestampIntervalValid) {

                blocksAtHeight.put(ByteBuffer.wrap(block.getHash()), block);
                registeredBlock = true;

                // Only keep the best three blocks at any level. For stability in the list, consider the just-added
                // block to be the highest-scored, and only remove another block if it has a higher score than the
                // new block.
                // TODO: make this bigger -- right now, it is small to test recovery when a good block is discarded
                // TODO: the check is bypassed while in the Genesis cycle, as it is less robust and we have no need to
                // TODO: harden it
                if (blocksAtHeight.size() > 3 && !BlockManager.inGenesisCycle()) {
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
            }
        }

        return registeredBlock;
    }

    // TODO: remove this from status updates and delete
    public static synchronized long bestScoreForHeight(long height) {

        long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
        Map<ByteBuffer, Block> blocksForHeight = unfrozenBlocks.get(height);
        long bestScore = Long.MAX_VALUE;
        for (Block block : blocksForHeight.values()) {
            bestScore = Math.min(bestScore, block.chainScore(frozenEdgeHeight));
        }

        return bestScore;
    }

    // TODO: remove this from status updates and change back to private
    public static long votingScoreThresholdForHeight(long height) {

        // l: leading edge height
        // b: block height
        // f: frozen edge height
        // t: voting score threshold
        // t1: threshold component 1; calculated relative to leading edge
        // t2: threshold component 2; calculated relative to frozen edge

        // A vote is cast when the voting score is less than or equal to the threshold, so a lower threshold
        // is stricter.

        // t = t1 - t2
        // t1 = l - b - 1
        // t2 = 2 * (b - f - 1)

        // t = l - b - 1 - 2 * (b - f - 1)
        // t = l - b - 1 - 2b + 2f + 2
        // t = l - 3b  + 2f + 1

        return leadingEdgeHeight() - 3L * height + 2L * BlockManager.getFrozenEdgeHeight() + 1;
    }

    public static synchronized void castVotes() {

        // This is the voting system documented in the white paper. The lowest chain score is found and limited to a
        // minimum value of zero. If this limited value is less than or equal to the threshold for the height, a
        // vote is cast.

        long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
        for (long height : unfrozenBlocks.keySet()) {

            // Only continue if we have not yet voted for this height and the threshold is non-negative.
            long threshold = votingScoreThresholdForHeight(height);
            if (!votesCast.contains(height) && threshold >= 0) {

                // Only continue if we have blocks.
                Map<ByteBuffer, Block> blocksForHeight = unfrozenBlocks.get(height);
                if (!blocksForHeight.isEmpty()) {

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

                    // If the best score is less than or equal to the threshold, cast a vote for the block.
                    if (Math.max(lowestChainScore, 0) <= threshold) {
                        castVote(lowestScoredBlock);  // guaranteed not null because it is set when the score is set
                    }
                }
            }
        }
    }

    private static synchronized void castVote(Block block) {

        // Ensure that we only cast one vote for each block height.
        if (!votesCast.contains(block.getBlockHeight())) {
            votesCast.add(block.getBlockHeight());

            // For cancellations, we only need to look back to the highest block that we voted for that is not on this
            // chain. If any earlier block was on this chain, its vote was cancelled when the vote on the other chain
            // was cast.
            List<Long> heightsToCancel = new ArrayList<>();
            long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
            Block previousBlock = block.getPreviousBlock();
            short numberOfVotesToCancel = 0;
            short numberOfVotesToSave = 0;
            while (previousBlock != null && previousBlock.getBlockHeight() > frozenEdgeHeight &&
                    numberOfVotesToCancel == 0) {

                BlockVote voteForHeight = BlockVoteManager.getLocalVoteForHeight(previousBlock.getBlockHeight());
                if (voteForHeight != null && !ByteUtil.arraysAreEqual(voteForHeight.getHash(),
                        previousBlock.getHash())) {
                    numberOfVotesToCancel = (short) (previousBlock.getBlockHeight() - frozenEdgeHeight);
                    numberOfVotesToSave = (short) (block.getBlockHeight() - previousBlock.getBlockHeight() - 1);
                    NotificationUtil.send("canceling " + numberOfVotesToCancel + " votes and saving " +
                            numberOfVotesToSave + " when voting on height " + block.getBlockHeight() +
                            "; mismatch at " + previousBlock.getBlockHeight() + ", frozen edge " + frozenEdgeHeight);
                }

                previousBlock = previousBlock.getPreviousBlock();
            }

            // Register the vote locally and send it to the network.
            BlockVote vote = new BlockVote(block.getBlockHeight(), block.getHash(), numberOfVotesToCancel,
                    numberOfVotesToSave);
            BlockVoteManager.registerVote(Verifier.getIdentifier(), vote, true);
            Message message = new Message(MessageType.BlockVote19, vote);
            Message.broadcast(message);
        }
    }

    public static synchronized void freezeBlocks() {

        long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
        long highestVoteHeight = frozenEdgeHeight;
        for (long height : BlockVoteManager.getHeights()) {
            highestVoteHeight = Math.max(highestVoteHeight, height);
        }

        // At each level, we have two possibilities:
        // - a single hash that exceeds the threshold on its own - we vote for it
        // - a set of hashes that exceed the threshold with cancellations - we

        // To compute these, we simply need to know the threshold, and we need to know vote totals for
        // each hash and for cancellations.

        // We start at the frozen edge and step from
        Block frozenEdge = BlockManager.frozenBlockForHeight(frozenEdgeHeight);
        List<BlockVoteTally> viableTallies = new ArrayList<>();
        viableTallies.add(new BlockVoteTally(frozenEdgeHeight, frozenEdge.getHash(), 1, 0, 0));

        while (!viableTallies.isEmpty()) {

            List<BlockVoteTally> talliesForHeight = BlockVoteManager.talliesExtending(viableTallies);
            List<BlockVoteTally> talliesToExtend = new ArrayList<>();
            BlockVoteTally tallyToFreeze = null;
            for (BlockVoteTally tally : talliesForHeight) {

                if (tally.isValid() && tally.getNumberOfHashVotes() > tally.getThreshold()) {
                    tallyToFreeze = tally;
                } else if (tally.getNumberOfHashVotes() + tally.getNumberOfCancelledVotes() > tally.getThreshold()) {
                    talliesToExtend.add(tally);
                }
            }

            if (tallyToFreeze != null) {
                // Get the block.
                Block block = unfrozenBlockAtHeight(tallyToFreeze.getHeight(), tallyToFreeze.getBlockHash());

                // If the block is not null, get all the blocks going back to the frozen edge. Typically, this will
                // only be a single block, but we may be freezing several, and must freeze in order starting with the
                // first block past the frozen edge.
                if (block != null) {
                    List<Block> blocksToFreeze = new ArrayList<>();
                    blocksToFreeze.add(block);
                    while (block.getPreviousBlock() != null && block.getBlockHeight() > frozenEdgeHeight + 1L) {
                        block = block.getPreviousBlock();
                        blocksToFreeze.add(0, block);
                    }

                    if (blocksToFreeze.get(0).getBlockHeight() == frozenEdgeHeight + 1L) {
                        for (Block blockToFreeze : blocksToFreeze) {
                            BlockManager.freezeBlock(blockToFreeze);
                        }
                    } else {
                        StringBuilder heightsToFreeze = new StringBuilder();
                        String separator = "";
                        for (Block blockToFreeze : blocksToFreeze) {
                            heightsToFreeze.append(separator).append(blockToFreeze.getBlockHeight());
                            separator = ", ";
                        }
                        NotificationUtil.sendOnce("issue trying to freeze blocks on " + Verifier.getNickname() +
                                ", heights of blocks are " + heightsToFreeze + ", frozen edge is " + frozenEdgeHeight);
                    }
                } else {

                    // When the block is null, send a request to try to get it from another node.
                    fetchMissingBlock(tallyToFreeze.getHeight(), tallyToFreeze.getBlockHash());
                }

                viableTallies = new ArrayList<>();
            } else {
                viableTallies = talliesToExtend;
            }
        }

        // Remove any blocks below the new frozen edge.
        Set<Long> unfrozenHeights = new HashSet<>(unfrozenBlocks.keySet());
        for (Long unfrozenHeight : unfrozenHeights) {
            if (unfrozenHeight <= frozenEdgeHeight) {
                unfrozenBlocks.remove(unfrozenHeight);
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
                    registerBlock(responseBlock);
                }
            }
        });
    }

    public static synchronized long leadingEdgeHeight() {

        // The leading edge is defined as the greatest block height open for processing at which a valid block without
        // a discontinuity exists.

        long leadingEdgeHeight = BlockManager.getFrozenEdgeHeight();
        long openEdgeHeight = BlockManager.openEdgeHeight(true);

        boolean heightIsContinuous = true;
        while (heightIsContinuous && leadingEdgeHeight < openEdgeHeight) {
            long height = leadingEdgeHeight + 1;
            Map<ByteBuffer, Block> blocksForHeight = unfrozenBlocks.get(height);
            heightIsContinuous = false;
            if (blocksForHeight != null) {
                for (Block block : blocksForHeight.values()) {
                    if (block.getContinuityState() == Block.ContinuityState.Continuous) {
                        heightIsContinuous = true;
                    }
                }
            }

            if (heightIsContinuous) {
                leadingEdgeHeight = height;
            }
        }

        return leadingEdgeHeight;
    }

    public static synchronized Block blockToExtendForHeight(long blockHeight) {

        Block blockToExtend = null;
        long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
        if (blockHeight <= frozenEdgeHeight) {
            blockToExtend = BlockManager.frozenBlockForHeight(blockHeight);
        } else {
            Map<ByteBuffer, Block> blocks = unfrozenBlocks.get(blockHeight);
            long verificationTimeThreshold = System.currentTimeMillis() - Block.minimumVerificationInterval;
            if (blocks != null) {
                for (Block block : blocks.values()) {
                    if (block.getVerificationTimestamp() < verificationTimeThreshold &&
                            (blockToExtend == null ||
                                    block.chainScore(frozenEdgeHeight) < blockToExtend.chainScore(frozenEdgeHeight))) {
                        blockToExtend = block;
                    }
                }
            }
        }

        return blockToExtend;
    }

    public static synchronized Set<Long> unfrozenBlockHeights() {

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

    public static synchronized List<Block> allUnfrozenBlocks() {

        List<Block> allBlocks = new ArrayList<>();
        for (Map<ByteBuffer, Block> blocks : unfrozenBlocks.values()) {
            allBlocks.addAll(blocks.values());
        }

        return allBlocks;
    }

    public static synchronized Block unfrozenBlockAtHeight(long height, byte[] hash) {

        Block block = null;
        Map<ByteBuffer, Block> blocksAtHeight = unfrozenBlocks.get(height);
        if (blocksAtHeight != null) {
            block = blocksAtHeight.get(ByteBuffer.wrap(hash));
        }

        return block;
    }

    public static synchronized void purge() {

        unfrozenBlocks.clear();
    }
}
