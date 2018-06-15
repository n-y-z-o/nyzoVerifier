package co.nyzo.verifier;

import co.nyzo.verifier.messages.BlockVote;
import co.nyzo.verifier.util.NotificationUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class ChainOptionManager {

    private static Set<Long> votesCast = new HashSet<>();
    private static Map<Long, List<Block>> unfrozenBlocks = new HashMap<>();

    public static synchronized boolean registerBlock(Block block) {

        boolean registeredBlock = false;

        // Reject all blocks with invalid signatures and all those at or behind the frozen edge or ahead of the open
        // edge.
        long frozenEdgeHeight = BlockManager.frozenEdgeHeight();
        if (block != null && block.getBlockHeight() > frozenEdgeHeight && block.signatureIsValid() &&
                block.getBlockHeight() <= BlockManager.openEdgeHeight(true)) {

            // Get the list of the blocks at this height.
            List<Block> blocksAtHeight = unfrozenBlocks.get(block.getBlockHeight());
            if (blocksAtHeight == null) {
                blocksAtHeight = new ArrayList<>();
                unfrozenBlocks.put(block.getBlockHeight(), blocksAtHeight);
            }

            // Check if the block is a simple duplicate (same signature).
            boolean alreadyContainsBlock = false;
            for (int i = 0; i < blocksAtHeight.size() && !alreadyContainsBlock; i++) {
                if (ByteUtil.arraysAreEqual(blocksAtHeight.get(i).getVerifierSignature(),
                        block.getVerifierSignature())) {
                    alreadyContainsBlock = true;
                }
            }

            // Check if the block is a duplicate verifier on the same previous block.
            boolean alreadyContainsVerifierOnSameChain = false;
            if (!alreadyContainsBlock) {
                for (int i = 0; i < blocksAtHeight.size() && !alreadyContainsVerifierOnSameChain; i++) {
                    if (ByteUtil.arraysAreEqual(blocksAtHeight.get(i).getVerifierIdentifier(),
                            block.getVerifierIdentifier()) &&
                            ByteUtil.arraysAreEqual(blocksAtHeight.get(i).getPreviousBlockHash(),
                                    block.getPreviousBlockHash())) {
                        alreadyContainsVerifierOnSameChain = true;
                    }
                }
            }

            if (!alreadyContainsBlock && !alreadyContainsVerifierOnSameChain) {
                blocksAtHeight.add(block);
                registeredBlock = true;

                // Only keep the best three blocks at any level. For stability in the list, consider the just-added
                // block to be the highest-scored, and only remove another block if it has a higher score than the
                // new block.
                if (blocksAtHeight.size() > 3) {
                    Block highestScoredBlock = block;
                    for (int i = 0; i < blocksAtHeight.size() - 1; i++) {
                        Block compareBlock = blocksAtHeight.get(i);
                        if (compareBlock.chainScore(frozenEdgeHeight) >
                                highestScoredBlock.chainScore(frozenEdgeHeight)) {
                            highestScoredBlock = compareBlock;
                        }
                    }

                    blocksAtHeight.remove(highestScoredBlock);
                }
            }
        }

        return registeredBlock;
    }

    // TODO: remove this from status updates and delete
    public static synchronized long bestScoreForHeight(long height) {

        long frozenEdgeHeight = BlockManager.frozenEdgeHeight();
        List<Block> blocksForHeight = unfrozenBlocks.get(height);
        long bestScore = Long.MAX_VALUE;
        if (!blocksForHeight.isEmpty()) {
            bestScore = blocksForHeight.get(0).chainScore(frozenEdgeHeight);
            for (int i = 1; i < blocksForHeight.size(); i++) {
                bestScore = Math.min(bestScore, blocksForHeight.get(i).chainScore(frozenEdgeHeight));
            }
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

        return leadingEdgeHeight() - 3L * height + 2L * BlockManager.frozenEdgeHeight() + 1;
    }

    public static synchronized void castVotes() {

        // This is the voting system documented in the white paper. The lowest chain score is found and limited to a
        // minimum value of zero. If this limited value is less than or equal to the threshold for the height, a
        // vote is cast.

        long frozenEdgeHeight = BlockManager.frozenEdgeHeight();
        for (long height : unfrozenBlocks.keySet()) {

            // Only continue if we have not yet voted for this height and if the threshold can be met.
            long threshold = votingScoreThresholdForHeight(height);
            if (!votesCast.contains(height) && threshold >= 0) {

                // Only continue if we have blocks and the threshold is non-negative.
                List<Block> blocksForHeight = unfrozenBlocks.get(height);
                if (!blocksForHeight.isEmpty()) {

                    // Find the block with the lowest score at this height.
                    Block lowestScoredBlock = blocksForHeight.get(0);
                    for (int i = 1; i < blocksForHeight.size(); i++) {
                        Block block = blocksForHeight.get(i);
                        if (block.chainScore(frozenEdgeHeight) < lowestScoredBlock.chainScore(frozenEdgeHeight)) {
                            lowestScoredBlock = block;
                        }
                    }

                    // If the best score is less than or equal to the threshold, cast a vote for the block.
                    if (Math.max(lowestScoredBlock.chainScore(frozenEdgeHeight), 0) <= threshold) {
                        castVote(lowestScoredBlock);
                    }
                }
            }
        }
    }

    private static synchronized void castVote(Block block) {

        // Ensure that we only cast one vote for each block height.
        if (!votesCast.contains(block.getBlockHeight())) {
            votesCast.add(block.getBlockHeight());

            // Register the vote locally and send it to the network.
            BlockVote vote = new BlockVote(block.getBlockHeight(), block.getHash());
            BlockVoteManager.registerVote(Verifier.getIdentifier(), vote, true);
            Message message = new Message(MessageType.BlockVote19, vote);
            Message.broadcast(message);
        }
    }

    private static synchronized boolean possiblyConnectedToFrozenChain(Block block) {

        boolean possiblyConnected = true;
        long frozenEdgeHeight = BlockManager.frozenEdgeHeight();
        if (block.getBlockHeight() <= frozenEdgeHeight) {
            possiblyConnected = false;
        } else {
            while (block != null && block.getBlockHeight() > frozenEdgeHeight + 1) {
                block = block.getPreviousBlock();
            }
            if (block != null && block.getBlockHeight() == frozenEdgeHeight + 1) {
                Block frozenEdgeBlock = BlockManager.frozenBlockForHeight(frozenEdgeHeight);
                possiblyConnected = ByteUtil.arraysAreEqual(frozenEdgeBlock.getHash(), block.getPreviousBlockHash());
            }
        }

        return possiblyConnected;
    }

    private static synchronized long chainHeightForBlock(Block block) {

        long height = block.getBlockHeight();
        long heightToCheck = height + 1;
        byte[] hash = block.getHash();
        boolean foundBreak = false;
        while (!foundBreak) {
            List<Block> blocks = unfrozenBlocks.get(heightToCheck);
            boolean foundHash = false;
            if (blocks != null) {
                for (int i = 0; i < blocks.size() && !foundHash; i++) {
                    Block blockToCheck = blocks.get(i);
                    if (ByteUtil.arraysAreEqual(blockToCheck.getPreviousBlockHash(), hash)) {
                        foundHash = true;
                        height = heightToCheck;
                        heightToCheck = height + 1;
                        hash = blockToCheck.getHash();
                    }
                }
            }

            if (!foundHash) {
                foundBreak = true;
            }
        }

        return height;
    }

    public static synchronized void freezeBlocks() {

        long frozenEdgeHeight = BlockManager.frozenEdgeHeight();
        for (long height = frozenEdgeHeight + 1L; height < leadingEdgeHeight(); height++) {

            byte[] hash = BlockVoteManager.winningHashForHeight(height);
            if (hash != null) {
                // Get the block.
                Block block = unfrozenBlockAtHeight(height, hash);

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
                        NotificationUtil.sendOnce("issue trying to freeze blocks on " + Verifier.getNickname());
                    }
                }
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

    public static synchronized long leadingEdgeHeight() {

        // The leading edge is defined as the greatest block height open for processing at which a valid block without
        // a discontinuity exists.

        long leadingEdgeHeight = -1;
        for (Long height : unfrozenBlocks.keySet()) {
            if (height > leadingEdgeHeight) {
                List<Block> blocksForHeight = unfrozenBlocks.get(height);
                for (int i = 0; i < blocksForHeight.size() && leadingEdgeHeight < height; i++) {
                    Block block = blocksForHeight.get(i);
                    if (block.getDiscontinuityState() == Block.DiscontinuityState.IsNotDiscontinuity) {
                        leadingEdgeHeight = height;
                    }
                }
            }
        }

        // The leading edge cannot be past the open edge.
        leadingEdgeHeight = Math.min(leadingEdgeHeight, BlockManager.openEdgeHeight(true));

        return leadingEdgeHeight;
    }

    // TODO: make this an instance method of the block class
    public static synchronized CycleInformation cycleInformationForBlock(Block block) {

        ByteBuffer blockVerifier = ByteBuffer.wrap(block.getVerifierIdentifier());
        ByteBuffer localVerifier = ByteBuffer.wrap(Verifier.getIdentifier());

        // Step backward through the chain until we find the beginning of the cycle.
        CycleInformation cycleInformation = null;
        Set<ByteBuffer> identifiers = new HashSet<>();
        long blockVerifierPreviousBlockHeight = -1;
        long localVerifierPreviousBlockHeight = -1;
        Block blockToCheck = block.getPreviousBlock();
        while (blockToCheck != null && cycleInformation == null) {

            ByteBuffer identifier = ByteBuffer.wrap(blockToCheck.getVerifierIdentifier());
            if (identifiers.contains(identifier)) {
                int cycleLength = (int) (block.getBlockHeight() - blockToCheck.getBlockHeight() - 1L);
                int blockVerifierIndexInCycle = blockVerifierPreviousBlockHeight < 0 ? -1 :
                        (int) (blockVerifierPreviousBlockHeight - blockToCheck.getBlockHeight() - 1L);
                int localVerifierIndexInCycle = localVerifierPreviousBlockHeight < 0 ? -1 :
                        (int) (localVerifierPreviousBlockHeight - blockToCheck.getBlockHeight() - 1L);
                cycleInformation = new CycleInformation(cycleLength, blockVerifierIndexInCycle,
                        localVerifierIndexInCycle);
            } else if (blockToCheck.getBlockHeight() == 0) {

                // For purposes of calculation, new verifiers in the first cycle of the chain are treated as existing
                // verifiers.
                int cycleLength = (int) block.getBlockHeight();
                int blockVerifierIndexInCycle = (int) Math.max(blockVerifierPreviousBlockHeight, 0);
                int localVerifierIndexInCycle = (int) Math.max(localVerifierPreviousBlockHeight, 0);

                cycleInformation = new CycleInformation(cycleLength, blockVerifierIndexInCycle,
                        localVerifierIndexInCycle);
            } else {
                identifiers.add(identifier);
            }

            if (identifier.equals(blockVerifier)) {
                blockVerifierPreviousBlockHeight = blockToCheck.getBlockHeight();
            } else if (identifier.equals(localVerifier)) {
                localVerifierPreviousBlockHeight = blockToCheck.getBlockHeight();
            }

            blockToCheck = blockToCheck.getPreviousBlock();
        }

        if (block.getBlockHeight() == 0) {
            cycleInformation = new CycleInformation(0, 0, 0);
        }

        return cycleInformation;
    }

    public static synchronized Block blockToExtendForHeight(long blockHeight) {

        Block blockToExtend = null;
        long frozenEdgeHeight = BlockManager.frozenEdgeHeight();
        if (blockHeight <= frozenEdgeHeight) {
            blockToExtend = BlockManager.frozenBlockForHeight(blockHeight);
        } else {
            List<Block> blocks = unfrozenBlocks.get(blockHeight);
            if (blocks != null) {
                for (Block block : blocks) {
                    if (blockToExtend == null ||
                            block.chainScore(frozenEdgeHeight) < blockToExtend.chainScore(frozenEdgeHeight)) {
                        blockToExtend = block;
                    } else if (block.chainScore(frozenEdgeHeight) == blockToExtend.chainScore(frozenEdgeHeight)) {

                        // This can only happen in the case of a new verifier.
                        System.out.println("chain score is equal for two blocks");
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
        List<Block> blocks = unfrozenBlocks.get(height);
        if (blocks != null) {
            number = blocks.size();
        }

        return number;
    }

    public static synchronized List<Block> allUnfrozenBlocks() {

        List<Block> allBlocks = new ArrayList<>();
        for (List<Block> blocks : unfrozenBlocks.values()) {
            allBlocks.addAll(blocks);
        }

        return allBlocks;
    }

    public static synchronized Block unfrozenBlockAtHeight(long height, byte[] hash) {

        Block block = null;
        List<Block> blocksAtHeight = unfrozenBlocks.get(height);
        if (blocksAtHeight != null) {
            for (Block blockToCheck : blocksAtHeight) {
                if (ByteUtil.arraysAreEqual(blockToCheck.getHash(), hash)) {
                    block = blockToCheck;
                }
            }
        }

        return block;
    }

    public static synchronized void purge() {

        unfrozenBlocks.clear();
    }
}
