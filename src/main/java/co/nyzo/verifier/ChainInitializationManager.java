package co.nyzo.verifier;

import co.nyzo.verifier.messages.BlockRequest;
import co.nyzo.verifier.messages.BlockResponse;
import co.nyzo.verifier.messages.BootstrapResponse;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.util.*;

public class ChainInitializationManager {

    private static final Map<Long, FrozenBlockVoteTally> hashVotes = new HashMap<>();
    private static final Map<Long, byte[]> blocksToFetch = new HashMap<>();
    static {
        runBlockFetchThread();
    }

    public static synchronized void processBootstrapResponseMessage(Message message) {

        System.out.println("processing node-join response");
        BootstrapResponse response = (BootstrapResponse) message.getContent();

        // Accumulate votes for the hashes.
        int numberOfHashes = response.getFrozenBlockHashes().size();
        for (int i = 0; i < numberOfHashes; i++) {
            long blockHeight = response.getFirstHashHeight() + i;
            FrozenBlockVoteTally voteTally = hashVotes.get(blockHeight);
            if (voteTally == null) {
                voteTally = new FrozenBlockVoteTally();
                hashVotes.put(blockHeight, voteTally);
            }

            byte[] hash = response.getFrozenBlockHashes().get(i);
            voteTally.vote(message.getSourceNodeIdentifier(), hash);
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
