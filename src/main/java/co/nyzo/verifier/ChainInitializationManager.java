package co.nyzo.verifier;

import co.nyzo.verifier.messages.BlockRequest;
import co.nyzo.verifier.messages.BlockResponse;
import co.nyzo.verifier.messages.NodeJoinResponse;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.util.*;

public class ChainInitializationManager {

    private static final Map<Long, FrozenBlockVoteTally> hashVotes = new HashMap<>();
    private static final Map<Long, byte[]> blocksToFetch = new HashMap<>();
    static {
        runBlockFetchThread();
    }

    public static synchronized void processNodeJoinResponse(Message message) {

        System.out.println("processing node-join response");
        NodeJoinResponse response = (NodeJoinResponse) message.getContent();

        // First, try to get the Genesis block if we don't have one already stored.
        Block localGenesisBlock = BlockManager.frozenBlockForHeight(0L);
        Block responseGenesisBlock = response.getGenesisBlock();
        if (localGenesisBlock == null && responseGenesisBlock != null) {
            if (Block.isValidGenesisBlock(responseGenesisBlock, null)) {
                System.out.println("GOT GENESIS BLOCK FROM NETWORK!!!");
                BlockManager.freezeBlock(responseGenesisBlock);
            }
        }

        // Accumulate votes for the hashes.
        int numberOfHashes = Math.min(response.getBlockHashes().size(), response.getBlockHeights().size());
        for (int i = 0; i < numberOfHashes; i++) {
            long blockHeight = response.getBlockHeights().get(i);
            FrozenBlockVoteTally voteTally = hashVotes.get(blockHeight);
            if (voteTally == null) {
                voteTally = new FrozenBlockVoteTally();
                hashVotes.put(blockHeight, voteTally);
            }

            // Vote. If consensus is reached on a hash, try to get the block from the network.
            byte[] hash = response.getBlockHashes().get(i);
            boolean consensus = voteTally.vote(message.getSourceNodeIdentifier(), response.getBlockHashes().get(i));
            if (consensus && blockHeight > BlockManager.highestBlockFrozen()) {
                System.out.println("*** need to get frozen block at height " + blockHeight + " from network ***");
                getBlockFromNetwork(blockHeight, hash);
            }
        }
    }

    private static synchronized void getBlockFromNetwork(long blockHeight, byte[] hash) {

        blocksToFetch.put(blockHeight, hash);
    }

    private static synchronized long nextBlockToFetch() {

        // Get the highest block in the fetch set.
        long heightToFetch = -1;
        Set<Long> heights = new HashSet<>(blocksToFetch.keySet());
        for (Long height : heights) {
            heightToFetch = Math.max(height, heightToFetch);
        }

        return heightToFetch;
    }

    private static synchronized byte[] hashForBlockHeight(long blockHeight) {

        return blocksToFetch.get(blockHeight);
    }

    private static void runBlockFetchThread() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!UpdateUtil.shouldTerminate()) {
                    try {
                    } catch (Exception ignored) { }

                    // Fetch the block. Only try getting from a single random node. If this is not successful, it will
                    // be attempted again on the next iteration.
                    long height = nextBlockToFetch();
                    if (height > 0) {
                        byte[] hash = hashForBlockHeight(height);
                        if (hash != null) {
                            List<Node> nodes = NodeManager.getMesh();
                            if (nodes.size() > 0) {
                                Node node = nodes.get(new Random().nextInt(nodes.size()));
                                Message message = new Message(MessageType.BlockRequest11, new BlockRequest(height, hash,
                                        true));
                                MessageCallback messageCallback = new MessageCallback() {
                                    @Override
                                    public void responseReceived(Message message) {
                                        BlockResponse response = (BlockResponse) message.getContent();
                                        if (response != null) {
                                            BlockManager.freezeBlock(response.getBlock());
                                        }
                                    }
                                };
                                Message.fetch(IpUtil.addressAsString(node.getIpAddress()), node.getPort(), message,
                                        false, messageCallback);
                            }
                        }
                    }
                }
            }
        }, "ChainInitializationManager-blockFetch").start();
    }
}
