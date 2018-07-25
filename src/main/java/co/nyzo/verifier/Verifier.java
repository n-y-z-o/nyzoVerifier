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
import java.util.concurrent.atomic.AtomicLong;

public class Verifier {

    public static final File dataRootDirectory = new File("/var/lib/nyzo");
    public static final File seedFundingFile = new File(dataRootDirectory, "seed_funding");

    private static final AtomicBoolean alive = new AtomicBoolean(false);
    private static byte[] privateSeed = null;
    private static String nickname = null;
    private static int rejoinCount = 0;

    private static int blocksCreated = 0;
    private static int blocksTransmitted = 0;

    private static Transaction seedFundingTransaction = null;

    private static int recentMessageTimestampsIndex = 0;
    private static final long[] recentMessageTimestamps = new long[10];

    private static final Map<ByteBuffer, Block> blocksExtended = new HashMap<>();

    private static boolean paused = false;

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
            NotificationUtil.send("setting temporary local verifier entry on " + Verifier.getNickname());
            NodeManager.addTempraryLocalVerifierEntry();

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

            // Send mesh requests to all trusted entry points. These will be used to send
            Message meshRequest = new Message(MessageType.MeshRequest15, null);
            for (TrustedEntryPoint entryPoint : trustedEntryPoints) {
                Message.fetch(entryPoint.getHost(), entryPoint.getPort(), meshRequest, new MessageCallback() {
                    @Override
                    public void responseReceived(Message message) {

                        // Send node-join requests to all nodes in the response.
                        MeshResponse response = (MeshResponse) message.getContent();
                        for (Node node : response.getMesh()) {
                            NodeManager.sendNodeJoinMessage(node.getIpAddress(), node.getPort());
                        }
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
            AtomicLong frozenEdgeStartHeight = new AtomicLong(-1);
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
                consensusFrozenEdge = ChainInitializationManager.frozenEdgeHeight(frozenEdgeHash,
                        frozenEdgeStartHeight);
                System.out.println("consensus frozen edge height: " + consensusFrozenEdge + ", start height " +
                        frozenEdgeStartHeight.get());
            }

            // If the consensus frozen edge is higher than the local frozen edge, fetch the necessary blocks to start
            // verifying.
            if (consensusFrozenEdge > BlockManager.getFrozenEdgeHeight()) {
                long startBlock = Math.max(BlockManager.getFrozenEdgeHeight() + 1, frozenEdgeStartHeight.get());
                System.out.println("need to fetch chain section " + startBlock + " to " + consensusFrozenEdge);
                ChainInitializationManager.fetchChainSection(startBlock, consensusFrozenEdge, frozenEdgeHash);
            }

            // Start the proactive side of the verifier, initiating the actions necessary to maintain the mesh and
            // build the blockchain.
            if (UpdateUtil.shouldTerminate() || consensusFrozenEdge > BlockManager.getFrozenEdgeHeight()) {
                alive.set(false);
                UpdateUtil.terminate();
                MeshListener.closeSocket();
                NotificationUtil.send("terminating verifier before main loop start on " + Verifier.getNickname() +
                        "; consensus frozen edge is " + consensusFrozenEdge + ", frozen edge is " +
                        BlockManager.getFrozenEdgeHeight());
            } else {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            NotificationUtil.send("started main verifier loop on " + getNickname());
                            StatusResponse.print();
                            verifierMain();
                        } catch (Exception reportOnly) {
                            NotificationUtil.send("exited verifierMain() on " + getNickname() + " due to exception: " +
                                    reportOnly.getMessage());
                        }
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
                BalanceList balanceList = BalanceListManager.balanceListForBlock(genesisBlock);
                BlockManager.freezeBlock(genesisBlock, genesisBlock.getPreviousBlockHash(), balanceList);
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

    public static Transaction getSeedFundingTransaction() {

        return seedFundingTransaction;
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
                PrintUtil.compactPrintByteArray(message.getSourceNodeIdentifier()) + ":" + response);

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

            long sleepTime = 1000L;
            try {
                // Only run the active verifier if connected to the mesh.
                if (NodeManager.connectedToMesh() && !paused) {

                    // If we have stopped receiving messages from the mesh, send new node-join messages. This is
                    // likely due to a changed IP address.
                    // TODO: re-implement this or remove
                    if (newestTimestampAge(1) > 5000L) {
                        rejoinCount++;
                    }

                    // Perform setup tasks for the NodeManager.
                    NodeManager.updateActiveVerifiersAndRemoveOldNodes();

                    // Clean up the map of blocks we have extended. We will never extend behind the frozen edge, so
                    // those can be removed. This map is used to ensure that we do not extend the same block more than
                    // once.
                    long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
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
                    for (long height = frozenEdgeHeight; height <= endHeight; height++) {

                        // Get the block to extend for the height from the chain option manager.
                        Block blockToExtend = UnfrozenBlockManager.blockToExtendForHeight(height);
                        if (blockToExtend != null && blockToExtend.getVerificationTimestamp() <
                                System.currentTimeMillis() - Block.minimumVerificationInterval) {
                            extendBlock(blockToExtend);
                        }
                    }

                    // The next steps are all about trying to freeze blocks. First, we freeze blocks based on votes
                    // we have received. Then, we cast votes based on the new state of the unfrozen blocks.
                    UnfrozenBlockManager.freezeBlocks();
                    UnfrozenBlockManager.castVotes();

                    // Remove old votes from the block vote manager.
                    BlockVoteManager.removeOldVotes();

                    // Request any votes that appear to be missing.
                    BlockVoteManager.requestMissingVotes();

                    // Request any blocks that appear to be missing.
                    BlockManager.requestMissingBlocks();

                    // Request any nodes that appear to be missing.
                    NodeManager.requestMissingNodes();

                    // If the frozen edge height has changed, update the new-verifier vote and update the frozen edge
                    // with the transaction pool.
                    if (frozenEdgeHeight != BlockManager.getFrozenEdgeHeight()) {
                        NewVerifierQueueManager.updateVote();
                        TransactionPool.updateFrozenEdge();
                    }
                }

            } catch (Exception reportOnly) {
                NotificationUtil.send("verifier main exception: " + PrintUtil.printException(reportOnly));
            }

            // Sleep for a short time to avoid consuming too much computational power.
            try {
                Thread.sleep(sleepTime);
            } catch (Exception ignored) { }
        }
    }

    private static void extendBlock(Block block) {

        CycleInformation cycleInformation = block.getCycleInformation();
        ByteBuffer blockHash = ByteBuffer.wrap(block.getHash());
        if (cycleInformation != null &&
                block.getContinuityState() == Block.ContinuityState.Continuous &&
                !blocksExtended.containsKey(blockHash)) {

            Block nextBlock = createNextBlock(block);
            blocksCreated++;

            if (nextBlock != null && nextBlock.getContinuityState() == Block.ContinuityState.Continuous) {

                blocksExtended.put(blockHash, block);

                boolean shouldTransmitBlock = UnfrozenBlockManager.registerBlock(nextBlock);
                if (shouldTransmitBlock) {
                    blocksTransmitted++;
                    Message.broadcast(new Message(MessageType.NewBlock9, new NewBlockMessage(nextBlock)));
                }
            } else if (nextBlock != null && nextBlock.getContinuityState() == Block.ContinuityState.Discontinuous) {

                // If the next block is a definite discontinuity, mark that it has been extended so we do not extend it
                // again.
                blocksExtended.put(blockHash, block);
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

            List<Transaction> approvedTransactions = BalanceManager.approvedTransactionsForBlock(transactions,
                    previousBlock);

            BalanceList previousBalanceList = BalanceListManager.balanceListForBlock(previousBlock);
            BalanceList balanceList = Block.balanceListForNextBlock(previousBlock, previousBalanceList,
                    approvedTransactions, Verifier.getIdentifier());
            if (balanceList != null) {
                long startTimestamp = BlockManager.startTimestampForHeight(blockHeight);
                block = new Block(blockHeight, previousBlock.getHash(), startTimestamp, approvedTransactions,
                        balanceList.getHash());
            }
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

    public static String getBlockCreationInformation() {

        return blocksTransmitted + "/" + blocksCreated;
    }

    public static boolean isPaused() {
        return paused;
    }

    public static void setPaused(boolean paused) {
        Verifier.paused = paused;
    }

    public static int getRejoinCount() {

        return rejoinCount;
    }
}
