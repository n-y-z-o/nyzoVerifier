package co.nyzo.verifier.client;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.*;
import co.nyzo.verifier.util.LogUtil;
import co.nyzo.verifier.util.ThreadUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientDataManager {

    private static final long meshUpdateInterval = 1000L * 60L * 5L;  // 5 minutes
    private static final long blockUpdateInterval = 1000L * 4L;       // 4 seconds

    public static void start() {

        System.out.println("starting client data manager");

        // Load the trusted entry points. These are needed for a few tasks in the initialization process.
        List<TrustedEntryPoint> trustedEntryPoints = Verifier.getTrustedEntryPoints();

        // Send a single ping to the first trusted entry point. This primes the messaging system and eliminates
        // initialization delays that may cause the first mesh message to be rejected due to timestamp issues and
        // replay protection.
        if (trustedEntryPoints.size() > 0) {
            TrustedEntryPoint entryPoint = trustedEntryPoints.get(0);
            Message.fetchTcp(entryPoint.getHost(), entryPoint.getPort(), new Message(MessageType.Ping200, null), null);
        }

        // Load the mesh from trusted entry points first, as this is needed for fetching other data.
        loadMesh(trustedEntryPoints);

        // Load the Genesis block. This will be a democratic process among the trusted entry points to fetch, then it
        // will be stored for future runs of the client.
        loadGenesisBlock(trustedEntryPoints);

        // Load the consensus frozen edge. This is the same process used by the verifier.
        ChainInitializationManager.initializeFrozenEdge(trustedEntryPoints);

        new Thread(new Runnable() {
            @Override
            public void run() {

                long lastMeshUpdateTimestamp = 0L;
                long lastBlockUpdateTimestamp = 0L;

                while (!UpdateUtil.shouldTerminate()) {

                    // Update the mesh.
                    if (lastMeshUpdateTimestamp < System.currentTimeMillis() - meshUpdateInterval) {
                        lastMeshUpdateTimestamp = System.currentTimeMillis();
                        updateMesh();
                    }

                    // Try to fetch a new block.
                    if (lastBlockUpdateTimestamp < System.currentTimeMillis() - blockUpdateInterval) {
                        lastBlockUpdateTimestamp = System.currentTimeMillis();
                        requestBlockWithVotes();
                    }

                    // Sleep for a short time to keep the loop from running too tightly.
                    ThreadUtil.sleep(300L);
                }
            }
        }).start();
    }

    private static void loadMesh(List<TrustedEntryPoint> trustedEntryPoints) {

        for (TrustedEntryPoint trustedEntryPoint : trustedEntryPoints) {

            Message message = new Message(MessageType.MeshRequest15, null);
            Message.fetchTcp(trustedEntryPoint.getHost(), trustedEntryPoint.getPort(), message, new MessageCallback() {
                @Override
                public void responseReceived(Message message) {
                    if (message.getContent() instanceof MeshResponse) {
                        ClientNodeManager.processMeshResponse((MeshResponse) message.getContent());
                    }
                }
            });
        }
    }

    private static void updateMesh() {

        Message message = new Message(MessageType.FullMeshRequest41, null);
        Node node = ClientNodeManager.randomNode();
        if (node != null) {
            Message.fetch(node, message, new MessageCallback() {
                @Override
                public void responseReceived(Message message) {
                    if (message.getContent() instanceof MeshResponse) {
                        ClientNodeManager.processMeshResponse((MeshResponse) message.getContent());
                    }
                }
            });
        }
    }

    private static void loadGenesisBlock(List<TrustedEntryPoint> trustedEntryPoints) {

        // In an effort to move toward greater decentralization, the client uses a democratic consensus process for
        // the Genesis block. This will also allow the client to work well with other Nyzo-based blockchains simply
        // by changing the trusted entry points.
        Block genesisBlock = BlockManager.frozenBlockForHeight(0L);
        if (genesisBlock == null) {

            AtomicInteger numberOfResponsesPending = new AtomicInteger(trustedEntryPoints.size());

            Map<ByteBuffer, Block> blocks = new ConcurrentHashMap<>();
            Map<ByteBuffer, AtomicInteger> hashCounts = new ConcurrentHashMap<>();
            Message message = new Message(MessageType.BlockRequest11, new BlockRequest(0, 0, false));
            for (TrustedEntryPoint trustedEntryPoint : trustedEntryPoints) {
                Message.fetchTcp(trustedEntryPoint.getHost(), trustedEntryPoint.getPort(), message,
                        new MessageCallback() {
                            @Override
                            public void responseReceived(Message message) {

                                if (message.getContent() instanceof BlockResponse) {
                                    BlockResponse response = (BlockResponse) message.getContent();
                                    if (response.getBlocks().size() > 0) {
                                        Block block = response.getBlocks().get(0);
                                        if (block.signatureIsValid() && block.getBlockHeight() == 0) {
                                            ByteBuffer hash = ByteBuffer.wrap(block.getHash());
                                            blocks.put(hash, block);
                                            synchronized (ClientDataManager.class) {
                                                AtomicInteger count = hashCounts.get(hash);
                                                if (count == null) {
                                                    count = new AtomicInteger(0);
                                                    hashCounts.put(hash, count);
                                                }
                                                count.incrementAndGet();
                                            }
                                        }
                                    }
                                }

                                numberOfResponsesPending.decrementAndGet();
                            }
                        });
            }

            // Wait up to 3 seconds for the responses to return.
            for (int i = 0; i < 10 && numberOfResponsesPending.get() > 0; i++) {
                System.out.println("Genesis block wait iteration " + i);
                ThreadUtil.sleep(300L);
            }

            // Find the hash with the highest count.
            int winningCount = 0;
            ByteBuffer winningHash = null;
            for (ByteBuffer hash : hashCounts.keySet()) {
                int count = hashCounts.get(hash).get();
                if (count > winningCount) {
                    winningHash = hash;
                    winningCount = count;
                }
            }

            // If a hash was found, save the Genesis block.
            if (winningHash != null) {
                genesisBlock = blocks.get(winningHash);

                BalanceList balanceList = BalanceListManager.balanceListForBlock(genesisBlock);
                BlockManager.freezeBlock(genesisBlock, genesisBlock.getPreviousBlockHash(), balanceList, null);
            }
        }
    }

    private static void requestBlockWithVotes() {

        // This method is borrowed, largely unchanged, from the sentinel process. The block-with-votes bundle is
        // fetched, and the block is immediately frozen if the data is sufficient.

        long heightToRequest = BlockManager.getFrozenEdgeHeight() + 1L;
        LogUtil.println("requesting block with votes for height " + heightToRequest);
        BlockWithVotesRequest request = new BlockWithVotesRequest(heightToRequest);
        Message message = new Message(MessageType.BlockWithVotesRequest37, request);
        Node node = ClientNodeManager.randomNode();
        if (node != null) {
            Message.fetch(node, message, new MessageCallback() {
                @Override
                public void responseReceived(Message message) {

                    BlockWithVotesResponse response = message == null ? null :
                            (BlockWithVotesResponse) message.getContent();
                    LogUtil.println("block-with-votes response is " + response);
                    if (response != null && response.getBlock() != null && !response.getVotes().isEmpty()) {

                        int voteThreshold = BlockManager.currentCycleLength() * 3 / 4;
                        int voteCount = 0;
                        byte[] blockHash = response.getBlock().getHash();

                        // Count the votes for the block.
                        for (BlockVote vote : response.getVotes()) {
                            if (ByteUtil.arraysAreEqual(blockHash, vote.getHash())) {
                                // Reconstruct the message in which the vote was originally sent. If the signature is
                                // valid and the verifier is in the cycle, count the vote.
                                Message voteMessage = new Message(vote.getMessageTimestamp(), MessageType.BlockVote19,
                                        vote, vote.getSenderIdentifier(), vote.getMessageSignature(),
                                        new byte[FieldByteSize.ipAddress]);
                                ByteBuffer senderIdentifier = ByteBuffer.wrap(vote.getSenderIdentifier());
                                if (voteMessage.isValid() && BlockManager.verifierInCurrentCycle(senderIdentifier)) {
                                    voteCount++;
                                }
                            }
                        }

                        // If the vote count exceeds the threshold, freeze the block.
                        LogUtil.println("block with votes: count=" + voteCount + ", threshold=" + voteThreshold);
                        if (voteCount > voteThreshold) {
                            BlockManager.freezeBlock(response.getBlock());
                        }
                    }
                }
            });
        }
    }
}
