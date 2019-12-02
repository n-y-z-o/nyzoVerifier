package co.nyzo.verifier.client;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.*;
import co.nyzo.verifier.util.LogUtil;
import co.nyzo.verifier.util.ThreadUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientDataManager {

    private static final long meshUpdateInterval = 1000L * 60L * 5L;  // 5 minutes
    private static final long blockUpdateInterval = 1000L * 4L;       // 4 seconds
    private static final long minimumReinitializationInterval = 1000L * 60L * 10L;  // 10 minutes
    private static final long reinitializationThreshold = 1000L * 60L * 10L;        // 10 minutes; about 86 blocks

    public static boolean start() {

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

        // Check this system's clock against the trusted entry points.
        long timeOffset = medianTimestampOffset(trustedEntryPoints);
        boolean started;
        if (Math.abs(timeOffset) > Message.replayProtectionInterval) {
            started = false;
            System.out.println(String.format("%scalculated time offset is %.1f; not starting data manager%s",
                    ConsoleColor.Red.background(), timeOffset / 1000.0, ConsoleColor.reset));
            System.out.println(ConsoleColor.Red.background() + "please check your system's clock" + ConsoleColor.reset);
        } else {
            started = true;

            if (Math.abs(timeOffset) > Message.replayProtectionInterval * 3 / 5) {
                System.out.println(String.format("%scalculated time offset is %.1f; messaging problems may occur%s",
                        ConsoleColor.Yellow.background(), timeOffset / 1000.0, ConsoleColor.reset));
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
                    long lastReinitializationTimestamp = System.currentTimeMillis();

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

                        // Reinitialize the frozen edge, if necessary. Both checks must be performed to avoid continuously
                        // reinitializing if the blockchain is stalled.
                        long timeSinceVerification = System.currentTimeMillis() -
                                BlockManager.getFrozenEdge().getVerificationTimestamp();
                        long timeSinceReinitialization = System.currentTimeMillis() - lastReinitializationTimestamp;
                        if (timeSinceVerification > reinitializationThreshold &&
                                timeSinceReinitialization > minimumReinitializationInterval) {
                            System.out.println(ConsoleColor.Yellow.background() + "reinitializing frozen edge, height=" +
                                    BlockManager.getFrozenEdgeHeight() + ConsoleColor.reset);
                            ChainInitializationManager.initializeFrozenEdge(trustedEntryPoints);
                            System.out.println(ConsoleColor.Yellow.background() + "reinitialized frozen edge, height=" +
                                    BlockManager.getFrozenEdgeHeight() + ConsoleColor.reset);
                            lastReinitializationTimestamp = System.currentTimeMillis();
                        }

                        // Sleep for a short time to keep the loop from running too tightly.
                        ThreadUtil.sleep(300L);
                    }
                }
            }).start();
        }

        return started;
    }

    private static long medianTimestampOffset(List<TrustedEntryPoint> trustedEntryPoints) {

        // Send timestamp requests to each trusted entry point.
        List<Long> offsets = new CopyOnWriteArrayList<>();
        AtomicInteger numberOfResponsesReceived = new AtomicInteger(0);
        for (TrustedEntryPoint trustedEntryPoint : trustedEntryPoints) {
            Message requestMessage = new Message(MessageType.TimestampRequest27, null);
            Message.fetchTcp(trustedEntryPoint.getHost(), trustedEntryPoint.getPort(), requestMessage,
                    new MessageCallback() {
                        @Override
                        public void responseReceived(Message responseMessage) {
                            if (responseMessage.getContent() instanceof TimestampResponse) {
                                // A response timestamp before the outgoing timestamp is a negative offset, a response
                                // timestamp after the current timestamp is a positive offset, and a response timestamp
                                // between the outgoing and current timestamp is an offset of 0.
                                TimestampResponse response = (TimestampResponse) responseMessage.getContent();
                                long currentTimestamp = System.currentTimeMillis();
                                if (response.getTimestamp() < requestMessage.getTimestamp()) {
                                    offsets.add(response.getTimestamp() - requestMessage.getTimestamp());
                                } else if (response.getTimestamp() > currentTimestamp) {
                                    offsets.add(response.getTimestamp() - currentTimestamp);
                                } else {
                                    offsets.add(0L);
                                }
                            }
                            numberOfResponsesReceived.incrementAndGet();
                        }
            });
        }

        // Wait up to 2 seconds, or until all responses have been received.
        for (int i = 0; i < 10 && numberOfResponsesReceived.get() < trustedEntryPoints.size(); i++) {
            ThreadUtil.sleep(200L);
        }

        // Return the median offset.
        long offset;
        if (offsets.isEmpty()) {
            offset = 0;
        } else {
            List<Long> offsetsCopy = new ArrayList<>(offsets);
            Collections.sort(offsetsCopy);
            if (offsetsCopy.size() % 2 == 0) {
                offset = (offsetsCopy.get(offsetsCopy.size() / 2 - 1) + offsetsCopy.get(offsetsCopy.size() / 2)) / 2;
            } else {
                offset = offsetsCopy.get(offsetsCopy.size() / 2);
            }
        }

        return offset;
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
        while (genesisBlock == null) {

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

            // If a hash was found, save the Genesis block. Otherwise, sleep 5 seconds before the next iteration.
            if (winningHash != null) {
                genesisBlock = blocks.get(winningHash);

                BalanceList balanceList = BalanceListManager.balanceListForBlock(genesisBlock);
                BlockManager.freezeBlock(genesisBlock, genesisBlock.getPreviousBlockHash(), balanceList, null);
            } else {
                // This sleep is not strictly necessary, but adding a small delay to problematic, unexpected situations
                // often allows the situation to resolve itself more easily. Without this sleep, this would still not be
                // a tight loop, due to the 3-second wait above.
                ThreadUtil.sleep(5000L);
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
