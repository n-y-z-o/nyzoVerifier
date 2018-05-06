package co.nyzo.verifier;

import co.nyzo.verifier.messages.BootstrapRequest;
import co.nyzo.verifier.messages.BootstrapResponse;
import co.nyzo.verifier.messages.NodeJoinMessage;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.SignatureUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Verifier {

    public static final File dataRootDirectory = new File("/var/lib/nyzo");

    private static final AtomicBoolean alive = new AtomicBoolean(false);
    private static byte[] privateSeed = null;

    private static int recentMessageTimestampsIndex = 0;
    private static final long[] recentMessageTimestamps = new long[10];

    private static Set<ByteBuffer> nodeJoinAcknowledgementsReceived = new HashSet<>();

    static {
        // This ensures the seed is always available, even if this class is used from a test script.
        loadPrivateSeed();
    }

    public static void main(String[] args) {

        if (args.length > 0) {
            try {
                List<String> arguments = new ArrayList<String>();
                for (int i = 1; i < args.length; i++) {
                    arguments.add(args[i]);
                }

                System.out.println("executing class " + args[0]);
                Class<?> classToRun = Class.forName(args[0]);
                Method mainMethod = classToRun.getDeclaredMethod("main", String[].class);
                mainMethod.invoke(classToRun, new Object[] { arguments.toArray(new String[arguments.size()]) });
                System.out.println("fin.");
            } catch (Exception e) {

                System.out.println("unable to load class " + args[0]);
            }

        } else {
            start();
        }
    }

    public static boolean isAlive() {
        return alive.get();
    }

    private static void loadPrivateSeed() {

        dataRootDirectory.mkdirs();

        if (privateSeed == null) {
            final Path seedFile = Paths.get(dataRootDirectory.getAbsolutePath() + "/verifier_private_seed");
            System.out.println("seed file path is " + seedFile);
            try {
                List<String> lines = Files.readAllLines(seedFile);
                if (lines != null && !lines.isEmpty()) {
                    String line = lines.get(0);
                    if (line.length() > 64) {
                        privateSeed = ByteUtil.byteArrayFromHexString(lines.get(0), 32);
                    }
                }
            } catch (Exception ignored) { }

            if (privateSeed == null || ByteUtil.isAllZeros(privateSeed) || privateSeed.length != 32) {
                privateSeed = KeyUtil.generateSeed();
                try {
                    Files.write(seedFile, Arrays.asList(ByteUtil.arrayAsStringWithDashes(privateSeed)));
                } catch (Exception e) {
                    e.printStackTrace();
                    privateSeed = null;
                }
            }
        }
    }

    public static void start() {

        if (!alive.getAndSet(true)) {

            // Load the private seed. This seed is used to sign all messages, so this is done first.
            loadPrivateSeed();
            nodeJoinAcknowledgementsReceived.add(ByteBuffer.wrap(getIdentifier()));  // avoids send node-join to self

            // Start the node listener and wait for it to start and for the port to settle.
            MeshListener.start();
            try {
                Thread.sleep(20L);
            } catch (Exception e) { }

            System.out.println("starting verifier");

            // Load the list of trusted entry points.
            List<String> trustedEntryPoints = getTrustedEntryPoints();
            System.out.println("trusted entry points");
            for (String entryPoint : trustedEntryPoints) {
                System.out.println("-" + entryPoint);
            }

            // Send node-list requests to all trusted entry points.



            // Attempt to connect to the mesh. This should succeed on the first attempt, but it may take longer if we
            // are starting a new mesh.
            long consensusFrozenEdge = -1;
            byte[] frozenEdgeHash = new byte[FieldByteSize.hash];
            AtomicInteger frozenEdgeCycleLength = new AtomicInteger(-1);
            while (consensusFrozenEdge < 0) {

                // Send bootstrap requests to all trusted entry points.
                Message bootstrapRequest = new Message(MessageType.BootstrapRequest1,
                        new BootstrapRequest(MeshListener.getPort(), true));
                for (String entryPoint : trustedEntryPoints) {
                    String[] split = entryPoint.split(":");
                    if (split.length == 2) {
                        String host = split[0];
                        int port = -1;
                        try {
                            port = Integer.parseInt(split[1]);
                        } catch (Exception ignored) {
                        }
                        if (!host.isEmpty() && port > 0) {
                            System.out.println("sending Bootstrap request to " + host + ":" + port);
                            Message.fetch(host, port, bootstrapRequest, false, new MessageCallback() {
                                @Override
                                public void responseReceived(Message message) {
                                    if (message == null) {
                                        System.out.println("Bootstrap response is null");
                                    } else {
                                        processBootstrapResponseMessage(message);
                                        sendNodeJoinRequests();
                                    }
                                }
                            });
                        }
                    }
                }

                // Wait 2 seconds for requests to return.
                try {
                    Thread.sleep(2000);
                } catch (Exception ignored) {
                }

                // Get the consensus frozen edge. If this can be determined, we can continue to the next step.
                consensusFrozenEdge = ChainInitializationManager.frozenEdgeHeight(frozenEdgeHash, frozenEdgeCycleLength);
                System.out.println("consensus frozen edge height: " + consensusFrozenEdge + ", cycle length " +
                        frozenEdgeCycleLength.get());
            }

            // If the consensus frozen edge is higher than the local frozen edge, fetch the necessary blocks to start
            // verifying.
            if (consensusFrozenEdge > BlockManager.highestBlockFrozen()) {
                long startBlock = Math.max(BlockManager.highestBlockFrozen(), consensusFrozenEdge -
                        5 * frozenEdgeCycleLength.get());
                System.out.println("need to fetch chain section " + startBlock + " to " + consensusFrozenEdge);
                ChainInitializationManager.fetchChainSection(startBlock, consensusFrozenEdge, frozenEdgeHash);
            }

            // Start the proactive side of the verifier, initiating whatever actions are necessary to maintain the mesh
            // and build the blockchain.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    verifierMain();
                    alive.set(false);
                }
            }, "Verifier-mainLoop").start();
        }
    }

    private static void sendNodeJoinRequests() {

        List<Node> mesh = NodeManager.getMesh();
        Message message = new Message(MessageType.NodeJoin3, new NodeJoinMessage());
        for (Node node : mesh) {
            ByteBuffer identifierBuffer = ByteBuffer.wrap(node.getIdentifier());
            if (!nodeJoinAcknowledgementsReceived.contains(identifierBuffer)) {
                Message.fetch(IpUtil.addressAsString(node.getIpAddress()), node.getPort(), message, false,
                        new MessageCallback() {
                    @Override
                    public void responseReceived(Message message) {
                        if (message != null) {
                            nodeJoinAcknowledgementsReceived.add(ByteBuffer.wrap(message.getSourceNodeIdentifier()));
                        }
                    }
                });
            }
        }
    }

    private static List<String> getTrustedEntryPoints() {

        Path path = Paths.get(dataRootDirectory.getAbsolutePath() + "/trusted_entry_points");
        List<String> entryPoints = new ArrayList<>();
        try {
            List<String> contentsOfFile = Files.readAllLines(path);
            for (String line : contentsOfFile) {
                line = line.trim();
                int indexOfHash = line.indexOf("#");
                if (indexOfHash >= 0) {
                    line = line.substring(0, indexOfHash).trim();
                }
                if (!line.isEmpty()) {
                    entryPoints.add(line);
                }
            }
        } catch (Exception ignored) { }

        return entryPoints;
    }

    private static void processBootstrapResponseMessage(Message message) {

        BootstrapResponse response = (BootstrapResponse) message.getContent();

        System.out.println("Got Bootstrap response from " +
                ByteUtil.arrayAsStringWithDashes(message.getSourceNodeIdentifier()) + ":" + response);

        // Add the nodes to the node manager.
        for (Node node : response.getMesh()) {
            NodeManager.updateNode(node.getIdentifier(), node.getIpAddress(), node.getPort(), node.isFullNode(),
                    node.getQueueTimestamp());
        }

        // Add the transactions to the transaction pool.
        for (Transaction transaction : response.getTransactionPool()) {
            TransactionPool.addTransaction(transaction);
        }

        // Add the unfrozen blocks to the chain-option manager.
        for (Block block : response.getUnfrozenBlockPool()) {
            ChainOptionManager.registerBlock(block);
        }

        // Cast hash votes with the chain initialization manager.
        ChainInitializationManager.processBootstrapResponseMessage(message);
    }

    private static void verifierMain() {

        while (!UpdateUtil.shouldTerminate()) {

            long sleepTime = 5000L;
            try {
                // Only run the active verifier if connected to the mesh.
                if (NodeManager.connectedToMesh()) {

                    long highestBlockFrozen = BlockManager.highestBlockFrozen();
                    long endHeight = Math.max(ChainOptionManager.leadingEdgeHeight(), highestBlockFrozen);
                    long startHeight = Math.max(endHeight - 2, highestBlockFrozen);
                    for (long height = startHeight; height <= endHeight; height++) {

                        // Try to extend the lowest-scoring block.
                        Block blockToExtend = ChainOptionManager.blockToExtendForHeight(height);
                        if (blockToExtend != null && blockToExtend.getDiscontinuityState() ==
                                Block.DiscontinuityState.IsNotDiscontinuity) {
                            Block nextBlock = createNextBlock(blockToExtend);
                            if (nextBlock != null) {
                                boolean shouldTransmitBlock = ChainOptionManager.registerBlock(nextBlock);
                                if (shouldTransmitBlock) {
                                    Message.broadcast(new Message(MessageType.NewBlock9, nextBlock));
                                }
                            }
                        } else {
                            System.out.println("have no block to extend at height " + height);
                        }
                    }

                    ChainOptionManager.removeAbandonedChains();
                    ChainOptionManager.freezeBlocks();

                    StringBuilder status = new StringBuilder("status: c=");
                    status.append(NodeManager.connectedToMesh()).append("/").append(NodeManager.getMesh().size());
                    status.append(";f=").append(BlockManager.highestBlockFrozen());
                    status.append(";L=").append(ChainOptionManager.leadingEdgeHeight());
                    for (Long height : ChainOptionManager.unfrozenBlockHeights()) {
                        status.append(";h=").append(height).append(",n=");
                        status.append(ChainOptionManager.numberOfBlocksAtHeight(height));
                    }
                    status.append(";t=").append(timestampAge());
                    System.out.println(status.toString());
                }

                // If messages from the network have stopped, reconnect.
                if (timestampAge() > 30L) {
                    //NodeManager.fetchNodeList(0);
                }

            } catch (Exception reportOnly) {
                System.err.println(PrintUtil.printException(reportOnly));
            }

            // Sleep for a short time to avoid consuming too much computational power.
            try {
                Thread.sleep(sleepTime);
            } catch (Exception ignored) { }
        }
    }

    private static Block createNextBlock(Block previousBlock) {

        Block block = null;
        if (previousBlock != null && !ByteUtil.arraysAreEqual(previousBlock.getVerifierIdentifier(),
                Verifier.getIdentifier())) {

            // Get the transactions for the block.
            long blockHeight = previousBlock.getBlockHeight() + 1L;
            List<Transaction> transactions = TransactionPool.transactionsForBlock(blockHeight);

            List<Transaction> approvedTransactions = BalanceManager.approvedTransactionsForBlock(transactions,
                    blockHeight);

            BalanceList balanceList = Block.balanceListForNextBlock(previousBlock, approvedTransactions,
                    Verifier.getIdentifier());
            long startTimestamp = BlockManager.startTimestampForHeight(blockHeight);
            block = new Block(blockHeight, previousBlock.getHash(), startTimestamp, approvedTransactions,
                    HashUtil.doubleSHA256(balanceList.getBytes()), balanceList);
        }

        return block;
    }

    public static byte[] getIdentifier() {

        return KeyUtil.identifierForSeed(privateSeed);
    }

    public static byte[] sign(byte[] bytesToSign) {

        return SignatureUtil.signBytes(bytesToSign, privateSeed);
    }

    public static synchronized void registerMessage() {

        recentMessageTimestamps[recentMessageTimestampsIndex] = System.currentTimeMillis();
        recentMessageTimestampsIndex = (recentMessageTimestampsIndex + 1) % recentMessageTimestamps.length;
    }

    private static synchronized long timestampAge() {

        return System.currentTimeMillis() - recentMessageTimestamps[recentMessageTimestampsIndex];
    }
}
