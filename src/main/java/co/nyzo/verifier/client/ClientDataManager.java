package co.nyzo.verifier.client;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.BlockRequest;
import co.nyzo.verifier.messages.BlockResponse;
import co.nyzo.verifier.messages.MeshResponse;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.ThreadUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientDataManager {

    private static final long meshUpdateInterval = 1000L * 60L * 5L;  // 5 minutes

    private static Block genesisBlock = null;
    private static Block frozenEdge = null;
    private static BalanceList frozenEdgeBalanceList = null;

    public static Block getFrozenEdge() {
        return frozenEdge;
    }

    public static BalanceList getFrozenEdgeBalanceList() {
        return frozenEdgeBalanceList;
    }

    public static void start() {

        System.out.println("starting client data manager");

        // Load the trusted entry points. These are needed for a few points in the initialization process.
        List<TrustedEntryPoint> trustedEntryPoints = Verifier.getTrustedEntryPoints();

        // Load the mesh from trusted entry points first, as this is needed for fetching other data.
        loadMesh(trustedEntryPoints);

        // Load the Genesis block. This will be a democratic process among the trusted entry points to fetch, then it
        // will
        loadGenesisBlock(trustedEntryPoints);

        // Get the local frozen edge.
        long localFrozenEdge = BlockManager.getFrozenEdgeHeight();
        System.out.println("local frozen edge: " + localFrozenEdge);

        new Thread(new Runnable() {
            @Override
            public void run() {

                long lastMeshUpdateTimestamp = 0L;

                while (!UpdateUtil.shouldTerminate()) {

                    // Update the mesh.
                    if (lastMeshUpdateTimestamp < System.currentTimeMillis() - meshUpdateInterval) {
                        lastMeshUpdateTimestamp = System.currentTimeMillis();
                        updateMesh();
                    }

                    ThreadUtil.sleep(300L);
                }
            }
        }).start();
    }

    private static void loadMesh(List<TrustedEntryPoint> trustedEntryPoints) {

        Message message = new Message(MessageType.MeshRequest15, null);
        for (TrustedEntryPoint trustedEntryPoint : trustedEntryPoints) {

            Message.fetch(trustedEntryPoint.getHost(), trustedEntryPoint.getPort(), message, new MessageCallback() {
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

        Message message = new Message(MessageType.MeshRequest15, null);
        Node node = ClientNodeManager.randomConfirmedNode();
        if (node != null) {
            String ipAddress = IpUtil.addressAsString(node.getIpAddress());
            Message.fetch(ipAddress, node.getPort(), message, new MessageCallback() {
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
        genesisBlock = BlockManager.frozenBlockForHeight(0L);
        if (genesisBlock == null) {

            AtomicInteger numberOfResponsesPending = new AtomicInteger(trustedEntryPoints.size());

            Map<ByteBuffer, Block> blocks = new ConcurrentHashMap<>();
            Map<ByteBuffer, AtomicInteger> hashCounts = new ConcurrentHashMap<>();
            Message message = new Message(MessageType.BlockRequest11, new BlockRequest(0, 0, false));
            for (TrustedEntryPoint trustedEntryPoint : trustedEntryPoints) {
                Message.fetch(trustedEntryPoint.getHost(), trustedEntryPoint.getPort(), message, new MessageCallback() {
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

                BalanceList balanceList = BalanceListManager.balanceListForBlock(genesisBlock, null);
                BlockManager.freezeBlock(genesisBlock, genesisBlock.getPreviousBlockHash(), balanceList, null);
            }
        }

        ThreadUtil.sleep(2000L);
    }
}
