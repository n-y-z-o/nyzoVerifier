package co.nyzo.verifier;

import co.nyzo.verifier.messages.*;
import co.nyzo.verifier.util.*;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Verifier {

    public static final File dataRootDirectory = new File("/var/lib/nyzo");
    public static final File seedFundingFile = new File(dataRootDirectory, "seed_funding");

    private static final AtomicBoolean alive = new AtomicBoolean(false);
    private static byte[] privateSeed = null;
    private static String nickname = null;
    private static int rejoinCount = 0;

    private static Transaction seedFundingTransaction = null;

    private static int recentMessageTimestampsIndex = 0;
    private static final long[] recentMessageTimestamps = new long[10];

    private static final Map<ByteBuffer, Block> blocksExtended = new HashMap<>();

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
                    FileUtil.writeFile(seedFile, Arrays.asList(ByteUtil.arrayAsStringWithDashes(privateSeed)));
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

            // Ensure that the Genesis block and the seed-funding transaction are loaded.
            loadGenesisBlock();
            loadSeedFundingTransaction();

            // Load the nickname. This is purely for display purposes.
            loadNickname();

            // Start the seed transaction manager. This loads all the seed transactions in the background.
            SeedTransactionManager.start();

            // Start the block file consolidator. This bundles every 1000 blocks into one file to avoiding using
            // too many inodes.
            BlockFileConsolidator.start();

            // Start the node listener and wait for it to start and for the port to settle.
            MeshListener.start();
            try {
                Thread.sleep(20L);
            } catch (Exception e) { }

            System.out.println("starting verifier");

            // Load the list of trusted entry points.
            List<TrustedEntryPoint> trustedEntryPoints = getTrustedEntryPoints();
            System.out.println("trusted entry points");
            for (TrustedEntryPoint entryPoint : trustedEntryPoints) {
                System.out.println("-" + entryPoint);
            }

            // Send mesh requests to all trusted entry points.
            Message meshRequest = new Message(MessageType.MeshRequest15, null);
            for (TrustedEntryPoint entryPoint : trustedEntryPoints) {
                Message.fetch(entryPoint.getHost(), entryPoint.getPort(), meshRequest, new MessageCallback() {
                    @Override
                    public void responseReceived(Message message) {

                        // Add the nodes to the mesh and send node-join requests.
                        MeshResponse response = (MeshResponse) message.getContent();
                        for (Node node : response.getMesh()) {
                            NodeManager.updateNode(node.getIdentifier(), node.getIpAddress(), node.getPort(),
                                    node.getQueueTimestamp());
                        }
                        sendNodeJoinRequests();
                    }
                });
            }

            // Wait two seconds for the node-join requests to reach other nodes so we start receiving blocks.
            try {
                Thread.sleep(2000L);
            } catch (Exception ignored) { }

            // Attempt to jump into the blockchain. This should succeed on the first attempt, but it may take longer if
            // we are starting a new mesh.
            long consensusFrozenEdge = -1;
            byte[] frozenEdgeHash = new byte[FieldByteSize.hash];
            AtomicInteger frozenEdgeCycleLength = new AtomicInteger(-1);
            while (consensusFrozenEdge < 0 && !UpdateUtil.shouldTerminate()) {

                // Send bootstrap requests to all trusted entry points.
                Message bootstrapRequest = new Message(MessageType.BootstrapRequest1,
                        new BootstrapRequest(MeshListener.getPort()));
                for (TrustedEntryPoint entryPoint : trustedEntryPoints) {

                    System.out.println("sending Bootstrap request to " + entryPoint);
                    Message.fetch(entryPoint.getHost(), entryPoint.getPort(), bootstrapRequest, new MessageCallback() {
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
            if (consensusFrozenEdge > BlockManager.frozenEdgeHeight()) {
                long startBlock = Math.max(BlockManager.frozenEdgeHeight() + 1, consensusFrozenEdge -
                        5 * frozenEdgeCycleLength.get());
                System.out.println("need to fetch chain section " + startBlock + " to " + consensusFrozenEdge);
                ChainInitializationManager.fetchChainSection(startBlock, consensusFrozenEdge, frozenEdgeHash);
            }

            // Start the proactive side of the verifier, initiating whatever actions are necessary to maintain the mesh
            // and build the blockchain.
            if (UpdateUtil.shouldTerminate()) {
                alive.set(false);
            } else {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationUtil.send("started main verifier loop on " + getNickname());
                        verifierMain();
                        alive.set(false);
                    }
                }, "Verifier-mainLoop").start();
            }
        }
    }

    public static void loadGenesisBlock() {

        Block genesisBlock = BlockManager.frozenBlockForHeight(0);
        while (genesisBlock == null && !UpdateUtil.shouldTerminate()) {
            try {

                URL url = new URL("https://s3-us-west-2.amazonaws.com/nyzo/genesis");
                ReadableByteChannel channel = Channels.newChannel(url.openStream());
                byte[] array = new byte[2048];
                ByteBuffer buffer = ByteBuffer.wrap(array);
                while (channel.read(buffer) > 0);
                channel.close();

                buffer.rewind();
                genesisBlock = Block.fromByteBuffer(buffer);

            } catch (Exception ignored) {
                ignored.printStackTrace();
            }

            // The verifier cannot start without a Genesis block. If there was a problem, sleep for two seconds and
            // try again.
            if (genesisBlock == null) {
                try {
                    Thread.sleep(2000L);
                } catch (Exception ignored) {
                }
            } else {
                BlockManager.freezeBlock(genesisBlock, genesisBlock.getPreviousBlockHash());
            }
        }
    }

    public static void loadSeedFundingTransaction() {

        while (seedFundingTransaction == null && !UpdateUtil.shouldTerminate()) {
            try {
                SeedTransactionManager.fetchFile(seedFundingFile);
                loadSeedFundingTransactionFromFile();

            } catch (Exception ignored) {
                ignored.printStackTrace();
            }

            // The verifier should not start without the transaction. If there was a problem, sleep for two seconds and
            // try again.
            if (seedFundingTransaction == null && !UpdateUtil.shouldTerminate()) {
                try {
                    Thread.sleep(2000L);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void loadSeedFundingTransactionFromFile() {

        try {
            byte[] fileBytes = Files.readAllBytes(Paths.get(seedFundingFile.getAbsolutePath()));
            ByteBuffer buffer = ByteBuffer.wrap(fileBytes);
            seedFundingTransaction = Transaction.fromByteBuffer(buffer);
        } catch (Exception ignored) { }
    }

    private static void sendNodeJoinRequests() {

        List<Node> mesh = NodeManager.getMesh();
        Message message = new Message(MessageType.NodeJoin3, new NodeJoinMessage());
        for (Node node : mesh) {
            ByteBuffer identifierBuffer = ByteBuffer.wrap(node.getIdentifier());
            if (!nodeJoinAcknowledgementsReceived.contains(identifierBuffer)) {
                String address = IpUtil.addressAsString(node.getIpAddress());
                Message.fetch(address, node.getPort(), message, new MessageCallback() {
                    @Override
                    public void responseReceived(Message message) {
                        if (message != null) {
                            nodeJoinAcknowledgementsReceived.add(ByteBuffer.wrap(message.getSourceNodeIdentifier()));

                            NodeJoinResponse response = (NodeJoinResponse) message.getContent();
                            if (response != null) {
                                NicknameManager.put(message.getSourceNodeIdentifier(), response.getNickname());
                                for (BlockVote vote : response.getBlockVotes()) {
                                    BlockVoteManager.registerVote(message.getSourceNodeIdentifier(), vote, false);
                                }
                                if (!ByteUtil.isAllZeros(response.getNewVerifierVote().getIdentifier())) {
                                    NewVerifierVoteManager.registerVote(message.getSourceNodeIdentifier(),
                                            response.getNewVerifierVote(), false);
                                }
                            }
                        }
                    }
                });
            }
        }
    }

    private static List<TrustedEntryPoint> getTrustedEntryPoints() {

        Path path = Paths.get(dataRootDirectory.getAbsolutePath() + "/trusted_entry_points");
        List<TrustedEntryPoint> entryPoints = new ArrayList<>();
        try {
            List<String> contentsOfFile = Files.readAllLines(path);
            for (String line : contentsOfFile) {
                line = line.trim();
                int indexOfHash = line.indexOf("#");
                if (indexOfHash >= 0) {
                    line = line.substring(0, indexOfHash).trim();
                }
                TrustedEntryPoint entryPoint = TrustedEntryPoint.fromString(line);
                if (entryPoint != null) {
                    entryPoints.add(entryPoint);
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
        // TODO: we should be able to remove this -- the mesh is already provided earlier from the trusted verifiers
        for (Node node : response.getMesh()) {
            NodeManager.updateNode(node.getIdentifier(), node.getIpAddress(), node.getPort(), node.getQueueTimestamp());
        }

        // Add the transactions to the transaction pool.
        for (Transaction transaction : response.getTransactionPool()) {
            TransactionPool.addTransaction(transaction);
        }

        // Add the unfrozen blocks to the chain-option manager.
        for (Block block : response.getUnfrozenBlockPool()) {
            UnfrozenBlockManager.registerBlock(block);
        }

        // Cast hash votes with the chain initialization manager.
        ChainInitializationManager.processBootstrapResponseMessage(message);
    }

    private static void verifierMain() {

        while (!UpdateUtil.shouldTerminate()) {

            StringBuilder status = new StringBuilder();
            long sleepTime = 1000L;
            try {
                // Only run the active verifier if connected to the mesh.
                if (NodeManager.connectedToMesh()) {

                    status.append("c");

                    // If we have stopped receiving messages from the mesh, send new node-join messages. This is
                    // likely due to a changed IP address.
                    if (newestTimestampAge(1) > 5000L) {
                        rejoinCount++;
                        nodeJoinAcknowledgementsReceived.clear();
                        sendNodeJoinRequests();
                        status.append(",rj");
                    }

                    // Perform setup tasks for the NodeManager.
                    NodeManager.updateActiveVerifiersAndRemoveOldNodes();

                    // Clean up the map of blocks we have extended. We will never extend behind the frozen edge, so
                    // those can be removed. This map is used to ensure that we do not extend the same block more than
                    // once.
                    long frozenEdgeHeight = BlockManager.frozenEdgeHeight();
                    for (ByteBuffer blockHash : new HashSet<>(blocksExtended.keySet())) {
                        Block block = blocksExtended.get(blockHash);
                        if (block.getBlockHeight() < frozenEdgeHeight) {
                            blocksExtended.remove(blockHash);
                        }
                    }

                    // Try to extend blocks from the frozen edge to the leading edge. Limit to one behind the open
                    // edge, because we cannot create a block that is not yet open (the block created is one higher
                    // than the block that is extended).
                    long endHeight = Math.min(Math.max(UnfrozenBlockManager.leadingEdgeHeight(), frozenEdgeHeight),
                            BlockManager.openEdgeHeight(false) - 1);
                    endHeight = Math.min(endHeight, frozenEdgeHeight + 10);  // TODO: remove this; for testing only
                    status.append(",e+").append(endHeight - frozenEdgeHeight);
                    for (long height = frozenEdgeHeight; height <= endHeight; height++) {

                        // Get the block to extend for the height from the chain option manager.
                        Block blockToExtend = UnfrozenBlockManager.blockToExtendForHeight(height);
                        if (blockToExtend == null) {
                            status.append(",n@+").append(height - frozenEdgeHeight);
                        }
                        if (blockToExtend != null && blockToExtend.getVerificationTimestamp() <
                                System.currentTimeMillis() - Block.minimumVerificationInterval) {
                            status.append(",b@+").append(height - frozenEdgeHeight);
                            extendBlock(blockToExtend, status);
                        }
                    }

                    // The next steps are all about trying to freeze blocks. First, we freeze blocks based on votes
                    // we have received. Then, we cast votes based on the new state of the unfrozen blocks.
                    UnfrozenBlockManager.freezeBlocks();
                    UnfrozenBlockManager.castVotes();

                    // Remove old votes from the block vote manager.
                    BlockVoteManager.removeOldVotes();

                    // If the frozen edge height has changed, update the new-verifier vote and update the frozen edge
                    // with the transaction pool.
                    if (frozenEdgeHeight != BlockManager.frozenEdgeHeight()) {
                        NewVerifierQueueManager.updateVote();
                        TransactionPool.updateFrozenEdge();
                    }

                    StatusResponse.setField("last cycle", status.toString());
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

    private static void extendBlock(Block block, StringBuilder status) {

        CycleInformation cycleInformation = block.getCycleInformation();
        ByteBuffer blockHash = ByteBuffer.wrap(block.getHash());
        if (cycleInformation != null &&
                //(cycleInformation.getLocalVerifierIndexInCycle() < cycleInformation.getCycleLength() / 2 ||
                 //       block.getBlockHeight() < 2L) &&
                block.getContinuityState() == Block.ContinuityState.Continuous &&
                !blocksExtended.containsKey(blockHash)) {

            blocksExtended.put(blockHash, block);
            Block nextBlock = createNextBlock(block);

            if (nextBlock != null && nextBlock.getContinuityState() == Block.ContinuityState.Continuous) {
                boolean shouldTransmitBlock = UnfrozenBlockManager.registerBlock(nextBlock);
                if (shouldTransmitBlock) {
                    status.append("(b)");
                    StatusResponse.setField("extended", nextBlock.getBlockHeight() + "");
                    Message.broadcast(new Message(MessageType.NewBlock9, nextBlock));
                } else {
                    status.append("(h)");
                }
            } else {
                status.append("(n)");
            }
        } else {
            if (blocksExtended.containsKey(blockHash)) {
                status.append("(ae)");
            } else {
                status.append("(ne)");
            }
        }
    }

    private static Block createNextBlock(Block previousBlock) {

        Block block = null;
        if (previousBlock != null && !ByteUtil.arraysAreEqual(previousBlock.getVerifierIdentifier(),
                Verifier.getIdentifier())) {

            // Get the transactions for the block.
            long blockHeight = previousBlock.getBlockHeight() + 1L;
            List<Transaction> transactions = TransactionPool.transactionsForHeight(blockHeight);
            if (blockHeight == 1) {
                transactions.add(seedFundingTransaction);
            } else {
                Transaction seedTransaction = SeedTransactionManager.transactionForBlock(blockHeight);
                if (seedTransaction != null) {
                    transactions.add(seedTransaction);
                }
            }
            System.out.println("have " + transactions.size() + " transactions before approval for block " +
                    blockHeight);

            List<Transaction> approvedTransactions = BalanceManager.approvedTransactionsForBlock(transactions,
                    previousBlock);
            System.out.println("have " + approvedTransactions.size() + " transactions after approval for block " +
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

    public static long newestTimestampAge(int offset) {

        int index = (recentMessageTimestampsIndex + recentMessageTimestamps.length - offset) %
                recentMessageTimestamps.length;
        return System.currentTimeMillis() - recentMessageTimestamps[index];
    }

    public static long oldestTimestampAge() {

        return System.currentTimeMillis() - recentMessageTimestamps[recentMessageTimestampsIndex];
    }

    public static long[] timestampAges() {

        long[] ages = new long[recentMessageTimestamps.length];
        long currentTime = System.currentTimeMillis();
        int offset = recentMessageTimestampsIndex;
        for (int i = 0; i < ages.length; i++) {
            ages[i] = currentTime - recentMessageTimestamps[(offset - i - 1 + ages.length) % ages.length];
        }

        return ages;
    }

    private static void loadNickname() {

        try {
            nickname = Files.readAllLines(Paths.get(dataRootDirectory.getAbsolutePath() + "/nickname")).get(0);
        } catch (Exception ignored) { }

        if (nickname == null) {
            nickname = "";
        }
        nickname = nickname.trim();

        if (nickname.isEmpty()) {
            nickname = PrintUtil.compactPrintByteArray(getIdentifier());
        }
        NicknameManager.put(getIdentifier(), nickname);
    }

    public static String getNickname() {

        if (nickname == null) {
            loadNickname();
        }

        return nickname;
    }

    public static int getRejoinCount() {

        return rejoinCount;
    }
}
