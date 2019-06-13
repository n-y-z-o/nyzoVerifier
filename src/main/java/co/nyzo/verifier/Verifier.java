package co.nyzo.verifier;

import co.nyzo.verifier.messages.*;
import co.nyzo.verifier.nyzoString.NyzoStringEncoder;
import co.nyzo.verifier.nyzoString.NyzoStringPrivateSeed;
import co.nyzo.verifier.nyzoString.NyzoStringPublicIdentifier;
import co.nyzo.verifier.util.*;
import co.nyzo.verifier.web.WebListener;

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

    public static final File dataRootDirectory = TestnetUtil.testnet ?  new File("/var/lib/nyzo/testnet") :
            new File("/var/lib/nyzo/production");

    private static final AtomicBoolean alive = new AtomicBoolean(false);
    private static byte[] privateSeed = null;
    private static String nickname = null;

    private static int numberOfBlocksCreated = 0;
    private static int numberOfBlocksTransmitted = 0;

    private static long lastBlockFrozenTimestamp = 0;

    private static final long blockWithVotesRequestInterval = 2000L;  // 2 seconds
    private static long lastBlockWithVotesRequestTimestamp = 0L;

    private static int recentMessageTimestampsIndex = 0;
    private static final long[] recentMessageTimestamps = new long[10];

    private static final Map<ByteBuffer, Block> blocksExtended = new HashMap<>();
    private static final Map<ByteBuffer, Block> blocksCreated = new HashMap<>();
    private static final Map<ByteBuffer, Block> blocksTransmitted = new HashMap<>();

    private static long initializationTime = 0L;

    static {
        // This ensures the seed is always available, even if this class is used from a test script.
        loadPrivateSeed();

        System.out.println("verifier identifier is " + ByteUtil.arrayAsStringWithDashes(getIdentifier()));
    }

    public static void main(String[] args) {

        if (args.length > 0) {

            try {
                List<String> arguments = new ArrayList<>();
                for (int i = 1; i < args.length; i++) {
                    arguments.add(args[i]);
                }

                System.out.println("executing class " + args[0]);
                Class<?> classToRun = Class.forName(args[0]);
                Method mainMethod = classToRun.getDeclaredMethod("main", String[].class);
                mainMethod.invoke(classToRun, new Object[]{arguments.toArray(new String[arguments.size()])});
                System.out.println("fin.");
            } catch (Exception e) {

                System.out.println("unable to load class " + args[0] + ", exception: " + PrintUtil.printException(e));
            }

        } else {
            RunMode.setRunMode(RunMode.Verifier);
            start();
        }
    }

    public static boolean isAlive() {
        return alive.get();
    }

    private static synchronized void loadPrivateSeed() {

        dataRootDirectory.mkdirs();

        if (privateSeed == null) {
            final Path seedFile = Paths.get(dataRootDirectory.getAbsolutePath() + "/verifier_private_seed");
            try {
                List<String> lines = Files.readAllLines(seedFile);
                if (!lines.isEmpty()) {
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

            // Write the information file with the Nyzo strings. These are typed, error-protected encodings of the
            // private seed and public identifier.
            if (privateSeed != null) {
                NyzoStringPrivateSeed privateSeedString = new NyzoStringPrivateSeed(privateSeed);
                byte[] publicIdentifier = KeyUtil.identifierForSeed(privateSeed);
                NyzoStringPublicIdentifier publicIdentifierString = new NyzoStringPublicIdentifier(publicIdentifier);

                Path informationFilePath = Paths.get(dataRootDirectory.getAbsolutePath() + "/verifier_info");
                FileUtil.writeFile(informationFilePath, Arrays.asList(NyzoStringEncoder.encode(privateSeedString),
                        NyzoStringEncoder.encode(publicIdentifierString)));
            }
        }
    }

    public static void start() {

        long startTimestamp = System.currentTimeMillis();
        if (!alive.getAndSet(true)) {

            // Load the private seed. This seed is used to sign all messages, so this is done first.
            loadPrivateSeed();
            NotificationUtil.send("setting temporary local verifier entry on " + Verifier.getNickname());
            NodeManager.addTemporaryLocalVerifierEntry();

            // Start the mesh listener and wait for it to start and for the port to settle.
            System.out.println("starting mesh listener");
            MeshListener.start();
            try {
                Thread.sleep(20L);
            } catch (Exception ignored) { }

            // Ensure that the Genesis block is loaded.
            System.out.println("loading genesis block");
            loadGenesisBlock();

            // Load the nickname. This is purely for display purposes.
            System.out.println("loading nickname");
            loadNickname();

            // Start the seed transaction manager. This loads all the seed transactions in the background.
            System.out.println("starting seed transaction manager");
            SeedTransactionManager.start();

            // Start the block file consolidator. This bundles every 1000 blocks into one file to avoiding using
            // too many inodes.
            System.out.println("starting block file consolidator");
            BlockFileConsolidator.start();

            System.out.println("starting verifier");

            // Load the list of trusted entry points.
            List<TrustedEntryPoint> trustedEntryPoints = getTrustedEntryPoints();
            System.out.println("trusted entry points");
            for (TrustedEntryPoint entryPoint : trustedEntryPoints) {
                System.out.println("-" + entryPoint);
            }
            if (trustedEntryPoints.isEmpty()) {
                System.err.println("trusted entry points list is empty -- unable to initialize");
                UpdateUtil.terminate();
            }

            // Send mesh requests to all trusted entry points.
            AtomicInteger numberOfMeshResponsesPending = new AtomicInteger(trustedEntryPoints.size());
            for (TrustedEntryPoint entryPoint : trustedEntryPoints) {
                fetchMesh(entryPoint, numberOfMeshResponsesPending);
                sendNodeJoinMessage(entryPoint);
            }

            // Wait up to two seconds for the mesh responses to return.
            long meshResponseWaitTime = 0L;
            for (int i = 0; i < 10 && numberOfMeshResponsesPending.get() > 0; i++) {
                ThreadUtil.sleep(200L);
                meshResponseWaitTime += 200L;
            }
            System.out.println(String.format("%d mesh responses pending after %.1f wait",
                    numberOfMeshResponsesPending.get(), meshResponseWaitTime / 1000.0));

            // Instruct the node manager to send the node-join messages. The queue is based on IP address, so deduping
            // naturally occurs and only one request is typically sent to each node at this point. The -1 value tells
            // the node manager to empty the queue.
            NodeManager.sendNodeJoinRequests(-1);

            ChainInitializationManager.initializeFrozenEdge(trustedEntryPoints);

            // In order to process efficiently, we need to be well-connected to the cycle. If there are slow-downs that
            // have prevented connection to this point, they should be addressed before entering the main verifier loop.
            // We set 75% of the current cycle as a threshold, as it is the minimum required for automatic consensus.
            NodeManager.sendNodeJoinRequests(-1);
            NodeManager.updateActiveVerifiersAndRemoveOldNodes();
            int meshRequestIndex = 0;
            while (NodeManager.getNumberOfActiveCycleIdentifiers() < BlockManager.currentCycleLength() * 3 / 4) {
                System.out.println(String.format("entering supplemental connection process because only %d in-cycle " +
                        "connections have been made for a cycle size of %d (%.1f%%)",
                        NodeManager.getNumberOfActiveCycleIdentifiers(), BlockManager.currentCycleLength(),
                        NodeManager.getNumberOfActiveCycleIdentifiers() * 100.0 / BlockManager.currentCycleLength()));
                System.out.println("missing in-cycle verifiers: " + NodeManager.getMissingInCycleVerifiers());

                // Fetch the mesh from one trusted entry point.
                numberOfMeshResponsesPending = new AtomicInteger(1);
                fetchMesh(trustedEntryPoints.get(meshRequestIndex), numberOfMeshResponsesPending);
                meshRequestIndex = (meshRequestIndex + 1) % trustedEntryPoints.size();

                // Wait up to two seconds for the mesh response to return.
                for (int i = 0; i < 10 && numberOfMeshResponsesPending.get() > 0; i++) {
                    ThreadUtil.sleep(200L);
                }

                // Clear the node-join request queue. Then, sleep one second to allow more requests to return, and wait
                // until the message queue has cleared. Finally, before the loop condition is checked again, update the
                // active verifiers to reflect any that have been added since the last iteration.
                NodeManager.sendNodeJoinRequests(-1);
                ThreadUtil.sleep(1000L);
                MessageQueue.blockThisThreadUntilClear();

                NodeManager.updateActiveVerifiersAndRemoveOldNodes();
            }

            // If the preference is set, start the web listener.
            if (PreferencesUtil.getBoolean(WebListener.startWebListenerKey, false)) {
                WebListener.start();
            }

            initializationTime = System.currentTimeMillis() - startTimestamp;
            System.out.println("ready to start thread for main verifier loop, initialization time=" +
                    initializationTime);

            // Start the proactive side of the verifier, initiating the actions necessary to maintain the mesh and
            // build the blockchain.
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

    private static void fetchMesh(TrustedEntryPoint entryPoint, AtomicInteger numberOfMeshResponsesPending) {

        Message meshRequest = new Message(MessageType.MeshRequest15, null);
        Message.fetchTcp(entryPoint.getHost(), entryPoint.getPort(), meshRequest, new MessageCallback() {
            @Override
            public void responseReceived(Message message) {

                // Enqueue node-join requests for all nodes in the response.
                MeshResponse response = (MeshResponse) message.getContent();
                for (Node node : response.getMesh()) {
                    NodeManager.enqueueNodeJoinMessage(node.getIpAddress(), node.getPortTcp());
                }

                numberOfMeshResponsesPending.decrementAndGet();
            }
        });
    }

    private static void sendNodeJoinMessage(TrustedEntryPoint trustedEntryPoint) {

        System.out.println("sending node-join messages to trusted entry point: " + trustedEntryPoint);

        Message message = new Message(MessageType.NodeJoin3, new NodeJoinMessage());
        Message.fetchTcp(trustedEntryPoint.getHost(), trustedEntryPoint.getPort(), message,
                new MessageCallback() {
                    @Override
                    public void responseReceived(Message message) {
                        if (message != null) {

                            NodeManager.updateNode(message);

                            NodeJoinResponse response = (NodeJoinResponse) message.getContent();
                            if (response != null) {

                                NicknameManager.put(message.getSourceNodeIdentifier(),
                                        response.getNickname());

                                if (!ByteUtil.isAllZeros(response.getNewVerifierVote().getIdentifier())) {
                                    NewVerifierVoteManager.registerVote(message.getSourceNodeIdentifier(),
                                            response.getNewVerifierVote(), false);
                                }
                            }
                        }
                    }
                });
    }

    public static void loadGenesisBlock() {

        Block genesisBlock = BlockManager.frozenBlockForHeight(0);
        while (genesisBlock == null && !UpdateUtil.shouldTerminate()) {
            System.out.println("genesis block is null");
            try {

                URL url = new URL(SeedTransactionManager.s3UrlForFile("genesis"));
                ReadableByteChannel channel = Channels.newChannel(url.openStream());
                byte[] array = new byte[2048];
                ByteBuffer buffer = ByteBuffer.wrap(array);
                while (channel.read(buffer) > 0) { }
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
                BlockManager.freezeBlock(genesisBlock, genesisBlock.getPreviousBlockHash(), balanceList, null);
            }
        }
    }

    public static List<TrustedEntryPoint> getTrustedEntryPoints() {

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
        } catch (Exception e) {
            System.out.println("issue getting trusted entry points: " + PrintUtil.printException(e));
        }

        return entryPoints;
    }

    private static void verifierMain() {

        while (!UpdateUtil.shouldTerminate()) {

            MessageQueue.blockThisThreadUntilClear();

            long sleepTime = 300L;
            try {
                // Only run the active verifier if connected to the mesh.
                if (NodeManager.connectedToMesh()) {

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

                    // Clean up the maps of blocks we have created and transmitted. These maps only need entries past
                    // the frozen edge. We can iterate over the keys from the created map, because the transmitted
                    // map is a subset of the created map.
                    for (ByteBuffer blockHash : new HashSet<>(blocksCreated.keySet())) {
                        Block block = blocksCreated.get(blockHash);
                        if (block.getBlockHeight() <= frozenEdgeHeight) {
                            blocksCreated.remove(blockHash);
                            blocksTransmitted.remove(blockHash);
                        }
                    }

                    // Try to extend the frozen edge. We extend the frozen edge if the minimum verification interval
                    // has passed and if the edge is open.
                    Block frozenEdge = BlockManager.frozenBlockForHeight(frozenEdgeHeight);
                    if (frozenEdge.getVerificationTimestamp() <=
                            System.currentTimeMillis() - Block.minimumVerificationInterval &&
                            frozenEdge.getBlockHeight() < BlockManager.openEdgeHeight(false)) {
                        extendBlock(frozenEdge);
                    }

                    // Now, transmit blocks with suitable scores that have not been transmitted yet.
                    for (ByteBuffer blockHash : new HashSet<>(blocksCreated.keySet())) {

                        Block block = blocksCreated.get(blockHash);
                        if (!blocksTransmitted.containsKey(blockHash) &&
                                block.getContinuityState() == Block.ContinuityState.Continuous) {

                            // Only transmit a block if other verifiers would cast a vote for it in the next 10 seconds
                            // and broadcasting would not get us blacklisted.
                            if (BlockManager.verifierInOrNearCurrentCycle(ByteBuffer.wrap(Verifier.getIdentifier())) &&
                                    block.getMinimumVoteTimestamp() <= System.currentTimeMillis() + 10000L) {

                                numberOfBlocksTransmitted++;
                                Message.broadcast(new Message(MessageType.NewBlock9, new NewBlockMessage(block)));

                                blocksTransmitted.put(blockHash, block);
                            }

                        }
                    }

                    // Update the local vote with the unfrozen block manager. This may change for several reasons,
                    // and it should always be updated before attempting to freeze a block.
                    UnfrozenBlockManager.updateVote();

                    // Attempt to register any blocks that were previously disconnected.
                    UnfrozenBlockManager.attemptToRegisterDisconnectedBlocks();

                    // Try to freeze blocks.
                    boolean frozeBlock = UnfrozenBlockManager.attemptToFreezeBlock();
                    if (!frozeBlock) {
                        UnfrozenBlockManager.attemptToFreezeChain();
                    }

                    // Remove old votes from the vote managers.
                    BlockVoteManager.removeOldVotes();
                    NewVerifierVoteManager.removeOldVotes();
                    VerifierRemovalManager.removeOldVotes();

                    // Vote requests and block requests should only happen if this verifier is in the cycle. Otherwise,
                    // other verifiers might blacklist this verifier.
                    if (inCycle()) {

                        // Request any frozen blocks that appear to be missing.
                        BlockVoteManager.requestMissingFrozenBlocks();

                        // Request any blocks that appear to be missing.
                        UnfrozenBlockManager.requestMissingBlocks();
                    } else {

                        // In-cycle verifiers do not allow other verifiers to request missing blocks or votes, as they
                        // would use considerable bandwidth to service such requests. Instead, they provide frozen
                        // blocks bundled with votes in a single message.
                        requestBlockWithVotes();
                    }

                    // These are operations that only have to happen when a block is frozen.
                    long newFrozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
                    if (frozenEdgeHeight != newFrozenEdgeHeight) {

                        System.out.println("cleaning up because block " + newFrozenEdgeHeight + " was frozen");

                        // If this verifier has not yet cast a vote for the height that was just frozen, cast it now
                        // to show that this verifier is actively participating in the mesh and to fortify the
                        // block-with-votes responses.
                        Block newFrozenEdge = BlockManager.frozenBlockForHeight(newFrozenEdgeHeight);
                        if (newFrozenEdge != null &&
                                BlockVoteManager.getLocalVoteForHeight(newFrozenEdgeHeight) == null) {
                            System.out.println("casting late vote for height " + newFrozenEdgeHeight);
                            UnfrozenBlockManager.castVote(newFrozenEdge.getBlockHeight(), newFrozenEdge.getHash());
                        }

                        // Request the mesh. This is called only when an edge is frozen, and the node manager maintains
                        // a counter to ensure it is only performed once per cycle.
                        NodeManager.requestMesh();

                        // Send up to 10 node-join requests. Previously, these were all sent when the mesh was
                        // requested. Now, they are enqueued and sent a few at a time to reduce the spike in network
                        // activity.
                        NodeManager.sendNodeJoinRequests(10);

                        // Update scores with the verifier performance manager and send votes.
                        long scoreUpdateHeight = newFrozenEdgeHeight - 1;
                        Block scoreUpdateBlock = BlockManager.frozenBlockForHeight(scoreUpdateHeight);
                        VerifierPerformanceManager.updateScoresForFrozenBlock(scoreUpdateBlock,
                                BlockVoteManager.votesForHeight(scoreUpdateHeight));
                        VerifierPerformanceManager.sendVotes();

                        // Update vote counts for verifier removal.
                        VerifierRemovalManager.updateVoteCounts();

                        // Perform blacklist and unfrozen block maintenance.
                        BlacklistManager.performMaintenance();
                        UnfrozenBlockManager.performMaintenance();

                        // Update the new-verifier vote. This is necessary if the previous choice is now in the
                        // cycle.
                        NewVerifierQueueManager.updateVote();

                        // Clean old transactions from the transaction pool.
                        TransactionPool.updateFrozenEdge();

                        // Every 100 blocks, demote all in-cycle nodes and write the queue timestamps to disk. This
                        // should be done infrequently, as it involves file access, and it does not need to happen
                        // frequently to be effective.
                        if (BlockManager.getFrozenEdgeHeight() % 100 == 0) {
                            NodeManager.demoteInCycleNodes();
                            NodeManager.persistNodes();
                        }

                        // Store the timestamp of when the block was frozen. This is used for determining when to
                        // cast a vote for the next block. It is also used for determining when to start requesting
                        // missing votes.
                        lastBlockFrozenTimestamp = System.currentTimeMillis();

                        // Since the frozen edge height has changed, reduce the sleep time to zero to allow the next
                        // block to be produced as quickly as possible.
                        sleepTime = 0L;
                    }
                }

            } catch (Exception reportOnly) {
                NotificationUtil.send("verifier main exception: " + PrintUtil.printException(reportOnly));
            }

            // Sleep for a short time to avoid consuming too much computational power.
            if (sleepTime > 0L) {
                try {
                    Thread.sleep(sleepTime);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void extendBlock(Block block) {

        CycleInformation cycleInformation = block.getCycleInformation();
        ByteBuffer blockHash = ByteBuffer.wrap(block.getHash());
        if (cycleInformation != null &&
                block.getContinuityState() == Block.ContinuityState.Continuous &&
                !ByteUtil.arraysAreEqual(block.getVerifierIdentifier(), Verifier.getIdentifier()) &&
                !blocksExtended.containsKey(blockHash)) {

            // Create the next block. If the next block is not null, mark that the previous block has been extended so
            // we do not extend it again. Also, if the next block is not discontinuous, add it to the set of blocks
            // that have been created and register it with UnfrozenBlockManager.
            Block nextBlock = createNextBlock(block);
            numberOfBlocksCreated++;
            if (nextBlock != null) {
                blocksExtended.put(blockHash, block);

                if (block.getContinuityState() != Block.ContinuityState.Discontinuous) {
                    ByteBuffer nextBlockHash = ByteBuffer.wrap(nextBlock.getHash());
                    blocksCreated.put(nextBlockHash, nextBlock);

                    UnfrozenBlockManager.registerBlock(nextBlock);
                }
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

            // Add the seed transaction, if one is available.
            Transaction seedTransaction = SeedTransactionManager.transactionForBlock(blockHeight);
            if (seedTransaction != null) {
                transactions.add(seedTransaction);
            }

            List<Transaction> approvedTransactions = BalanceManager.approvedTransactionsForBlock(transactions,
                    previousBlock);

            BalanceList previousBalanceList = BalanceListManager.balanceListForBlock(previousBlock);
            if (previousBalanceList != null) {

                // Remove any balance-list spam transactions. To avoid rejection of incoming blocks, these
                // transactions are not currently removed from those blocks, but they are removed from blocks
                // produced locally.
                Map<ByteBuffer, Long> balanceMap = BalanceManager.makeBalanceMap(previousBalanceList);
                approvedTransactions = BalanceManager.transactionsWithoutBalanceListSpam(balanceMap,
                        approvedTransactions);

                // Make the balance list for the new block. If the balance list is good, make the block.
                BalanceList balanceList = Block.balanceListForNextBlock(previousBlock, previousBalanceList,
                        approvedTransactions, Verifier.getIdentifier());
                if (balanceList != null) {
                    long startTimestamp = BlockManager.startTimestampForHeight(blockHeight);
                    block = new Block(blockHeight, previousBlock.getHash(), startTimestamp, approvedTransactions,
                            balanceList.getHash());
                }
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

        return numberOfBlocksTransmitted + "/" + numberOfBlocksCreated;
    }

    public static long getLastBlockFrozenTimestamp() {

        return lastBlockFrozenTimestamp;
    }

    public static boolean inCycle() {

        return BlockManager.verifierInCurrentCycle(ByteBuffer.wrap(getIdentifier()));
    }

    private static void requestBlockWithVotes() {

        long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
        if (BlockManager.openEdgeHeight(false) > frozenEdgeHeight + 2 &&
                System.currentTimeMillis() > lastBlockWithVotesRequestTimestamp + blockWithVotesRequestInterval) {

            long interval = System.currentTimeMillis() - lastBlockWithVotesRequestTimestamp;
            lastBlockWithVotesRequestTimestamp = System.currentTimeMillis();

            long heightToRequest = frozenEdgeHeight + 1L;
            System.out.println("requesting block with votes for height " + heightToRequest + ", interval=" +
                    interval);
            BlockWithVotesRequest request = new BlockWithVotesRequest(heightToRequest);
            Message message = new Message(MessageType.BlockWithVotesRequest37, request);
            Message.fetchFromRandomNode(message, new MessageCallback() {
                @Override
                public void responseReceived(Message message) {

                    BlockWithVotesResponse response = message == null ? null :
                            (BlockWithVotesResponse) message.getContent();
                    if (response != null && response.getBlock() != null && !response.getVotes().isEmpty()) {

                        UnfrozenBlockManager.registerBlock(response.getBlock());

                        for (BlockVote vote : response.getVotes()) {

                            // Reconstruct the message in which the vote was originally sent. If the signature is valid,
                            // register the vote.
                            Message voteMessage = new Message(vote.getMessageTimestamp(), MessageType.BlockVote19, vote,
                                    vote.getSenderIdentifier(), vote.getMessageSignature(),
                                    new byte[FieldByteSize.ipAddress]);
                            if (voteMessage.isValid()) {
                                BlockVoteManager.registerVote(voteMessage);
                            }
                        }
                    }
                }
            });
        }
    }

}
