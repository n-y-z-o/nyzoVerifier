package co.nyzo.verifier.sentinel;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.messages.*;
import co.nyzo.verifier.util.*;
import co.nyzo.verifier.web.WebListener;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Sentinel {

    private static final AtomicBoolean loadedManagedVerifiers = new AtomicBoolean(false);
    public static final File managedVerifiersFile = new File(Verifier.dataRootDirectory, "managed_verifiers");

    private static final long whitelistUpdateInterval = Message.dynamicWhitelistInterval / 2;
    private static final long meshUpdateInterval = 1000L * 60L * 5L;  // 5 minutes
    private static final long blockUpdateIntervalFast = 1000L;
    private static final long blockUpdateIntervalStandard = 2000L;
    private static final long minimumLoopInterval = 3000L;

    private static final long blockCreationDelay = 20000L;
    private static final long blockTransmissionDelay = 10000L;

    // This is an additional safeguard to avoid spamming the mesh with blocks if the sentinel has somehow gotten into
    // a bad state.
    private static long lastBlockTransmissionTimestamp = 0L;
    private static final long minimumBlockTransmissionInterval = 30000L;

    private static Map<ByteBuffer, AtomicInteger> consecutiveSuccessfulBlockFetches = new ConcurrentHashMap<>();
    private static Map<ByteBuffer, Boolean> inFastFetchMode = new ConcurrentHashMap<>();

    private static int numberOfBlocksReceived = 0;
    private static int numberOfBlocksFrozen = 0;
    private static boolean calculatingValidChainScores = false;

    private static AtomicLong lastBlockReceivedTimestamp = new AtomicLong(0L);

    private static final List<ManagedVerifier> verifierList = new CopyOnWriteArrayList<>();
    private static final Map<ByteBuffer, ManagedVerifier> verifierMap = new ConcurrentHashMap<>();

    private static final Set<Block> blocksCreatedForManagedVerifiers = ConcurrentHashMap.newKeySet();
    private static final String lastBlockTransmissionHeightKey = "sentinel_last_block_transmission_height";
    private static long lastBlockTransmissionHeight = PersistentData.getLong(lastBlockTransmissionHeightKey, -1L);
    private static final String lastBlockTransmissionStringKey = "sentinel_last_block_transmitted";
    private static String lastBlockTransmissionString = PersistentData.get(lastBlockTransmissionStringKey);
    private static final String lastBlockTransmissionResultsKey = "sentinel_last_block_transmission_results";
    private static String lastBlockTransmissionResults = PersistentData.get(lastBlockTransmissionResultsKey);

    private static final Map<ByteBuffer, List<Node>> verifierIdentifierToMeshMap = new ConcurrentHashMap<>();

    private static Block frozenEdge = null;


    public static void main(String[] args) {

        // Set the run mode and initialize the block manager.
        RunMode.setRunMode(RunMode.Sentinel);
        BlockManager.initialize();

        // Start the web listener and the seed transaction manager.
        WebListener.start();
        SeedTransactionManager.start();

        // Start the main sentinel loop.
        start();
    }

    private static void start() {

        int loopCount = 0;

        Verifier.loadGenesisBlock();
        BlockFileConsolidator.start();

        // Load the managed verifiers. These are the verifiers for which the sentinel will produce blocks, if
        // necessary, and they are also used as data sources.
        if (!loadedManagedVerifiers.getAndSet(true)) {
            loadManagedVerifiers();
        }

        // Send a whitelist request to each managed verifier for which a private key is available.
        for (ManagedVerifier verifier : verifierList) {
            if (verifier.hasPrivateKey()) {
                sendWhitelistRequest(verifier);
            }
        }

        // Sleep for 1.5 seconds to give the whitelist requests time to process.
        ThreadUtil.sleep(1500L);

        // Initialize the frozen edge. This process will repeat until it is successful.
        SentinelUtil.initializeFrozenEdge(verifierList);

        // Store the frozen edge locally.
        frozenEdge = BlockManager.getFrozenEdge();

        // This timestamp is set whenever we receive a block so we do not try to create a block too soon
        // afterwards.
        lastBlockReceivedTimestamp.set(System.currentTimeMillis());

        // Start a separate thread for fetching data from each of the managed verifiers. This may generate some
        // redundant work, but it will ensure that the sentinel continues to function properly even if a large segment
        // of the verifiers fail simultaneously.
        int querySlot = 0;
        for (ManagedVerifier verifier : verifierList) {
            startThreadForVerifier(verifier, querySlot++);
        }

        // Start a single thread as a fallback for fetching blocks from the full cycle if all managed verifiers become
        // unresponsive.
        startFullCycleThread();

        // Start a single thread for transmitting blocks for new verifiers.
        startNewVerifierThread();

        // Start the thread for transmitting blocks. While a separate thread is needed for fetching data from each
        // managed verifier, only one thread is required for transmitting blocks, as only a single block at each height
        // will protect all verifiers, regardless of how many are down at that time.
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Set the last-block received timestamp so we do not immediately transmit a block.
                lastBlockReceivedTimestamp.set(System.currentTimeMillis());

                // Run the main loop.
                while (!UpdateUtil.shouldTerminate()) {
                    transmitBlockIfNecessary();
                    ThreadUtil.sleep(1000L);
                }
            }
        }).start();
    }

    private static void startThreadForVerifier(ManagedVerifier verifier, int querySlot) {

        new Thread(new Runnable() {
            @Override
            public void run() {

                ByteBuffer identifier = ByteBuffer.wrap(verifier.getIdentifier());
                long lastBlockRequestedTimestamp = 0L;
                long lastMeshUpdateTimestamp = 0L;
                long lastWhitelistTimestamp = 0L;
                while (!UpdateUtil.shouldTerminate()) {

                    long loopStartTimestamp = System.currentTimeMillis();

                    // This is an important step, though one that should be used with care in a situation with many
                    // threads sending messages simultaneously. As all the individual verifier loops are invoking this
                    // method, none will monopolize the queue.
                    MessageQueue.blockThisThreadUntilClear();

                    // If the private key for the verifier is available, send a whitelist request.
                    if (lastWhitelistTimestamp < System.currentTimeMillis() - whitelistUpdateInterval) {
                        lastWhitelistTimestamp = System.currentTimeMillis();
                        if (verifier.hasPrivateKey()) {
                            sendWhitelistRequest(verifier);
                        }
                    }

                    // Update the mesh. We need to know who the in-cycle nodes are in order to send out a block if one
                    // needs to be created.
                    if (lastMeshUpdateTimestamp < System.currentTimeMillis() - meshUpdateInterval) {
                        lastMeshUpdateTimestamp = System.currentTimeMillis();
                        updateMesh(verifier);
                    }

                    // Update the blocks. Both the fast-fetch setting and the interval are applied on a per-verifier
                    // basis. If we are certain that we are close to the actual frozen edge of the blockchain, we query
                    // only in our assigned slot to improve efficiency. Otherwise, all threads operate in parallel.
                    long blockUpdateInterval = inFastFetchMode.get(identifier) ? blockUpdateIntervalFast :
                            blockUpdateIntervalStandard;
                    int currentSlot = (int) ((System.currentTimeMillis() / blockUpdateInterval) % verifierList.size());
                    if (lastBlockRequestedTimestamp < System.currentTimeMillis() - blockUpdateInterval) {
                        if (currentSlot == querySlot ||
                                frozenEdge.getVerificationTimestamp() < System.currentTimeMillis() - 20000L) {
                            lastBlockRequestedTimestamp = System.currentTimeMillis();
                            updateBlocks(verifier);
                            verifier.setQueriedLastInterval(true);
                        } else {
                            verifier.setQueriedLastInterval(false);
                        }
                    }

                    // Ensure a minimum interval between iterations. This includes processing time, so the actual sleep
                    // time may be zero.
                    while (System.currentTimeMillis() < loopStartTimestamp + minimumLoopInterval) {
                        ThreadUtil.sleep(300L);
                    }
                }
            }
        }).start();
    }

    private static void startFullCycleThread() {

        new Thread(new Runnable() {
            @Override
            public void run() {

                long lastBlockRequestedTimestamp = 0L;
                while (!UpdateUtil.shouldTerminate()) {

                    long loopStartTimestamp = System.currentTimeMillis();

                    try {
                        // This is an important step, though one that should be used with care in a situation with many
                        // threads sending messages simultaneously. All the verifier loops are also invoking this
                        // method, so none of the threads will monopolize the message queue.
                        MessageQueue.blockThisThreadUntilClear();

                        // Update the blocks. This method is only used as a second-level fallback when full-parallel
                        // querying of managed verifiers is not providing blocks.
                        if (lastBlockRequestedTimestamp < System.currentTimeMillis() - blockUpdateIntervalStandard &&
                                (frozenEdge.getVerificationTimestamp() < System.currentTimeMillis() - 35000L)) {
                            lastBlockRequestedTimestamp = System.currentTimeMillis();

                            requestBlockWithVotes();
                        }
                    } catch (Exception e) {
                        System.out.println("exception in full-mesh thread main loop: " + PrintUtil.printException(e));
                    }

                    // Ensure a minimum interval between iterations. This includes processing time, so the actual sleep
                    // time may be zero.
                    while (System.currentTimeMillis() < loopStartTimestamp + minimumLoopInterval) {
                        ThreadUtil.sleep(300L);
                    }
                }
            }
        }).start();
    }

    private static void requestBlockWithVotes() {

        // This method is a simplification of the verifier process. The block-with-votes bundle is fetched, and the
        // block is immediately frozen if the data is sufficient.

        long heightToRequest = frozenEdge.getBlockHeight() + 1L;
        System.out.println("requesting block with votes for height " + heightToRequest);
        BlockWithVotesRequest request = new BlockWithVotesRequest(heightToRequest);
        Message message = new Message(MessageType.BlockWithVotesRequest37, request);
        Message.fetchFromRandomNode(message, new MessageCallback() {
            @Override
            public void responseReceived(Message message) {

                BlockWithVotesResponse response = message == null ? null :
                        (BlockWithVotesResponse) message.getContent();
                LogUtil.println("block-with-votes response is " + response);
                if (response != null && response.getBlock() != null && response.getBlock().signatureIsValid() &&
                        !response.getVotes().isEmpty()) {

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
                    System.out.println("block with votes: count=" + voteCount + ", threshold=" + voteThreshold);
                    if (voteCount > voteThreshold) {
                        freezeBlock(response.getBlock());
                    }
                }
            }
        });
    }

    public static Node randomNode() {

        Node node = null;

        // This is a nice balance of reasonable randomness and reasonable performance.
        List<ByteBuffer> verifierIdentifiers = new ArrayList<>(verifierIdentifierToMeshMap.keySet());
        Collections.shuffle(verifierIdentifiers);
        Random random = new Random();
        while (node == null && !verifierIdentifiers.isEmpty()) {
            ByteBuffer verifierIdentifier = verifierIdentifiers.remove(0);
            List<Node> nodes = new ArrayList<>(verifierIdentifierToMeshMap.get(verifierIdentifier));
            if (!nodes.isEmpty()) {
                node = nodes.get(random.nextInt(nodes.size()));
            }
        }

        return node;
    }

    private static void startNewVerifierThread() {

        new Thread(new Runnable() {
            @Override
            public void run() {

                long lastTransmissionHeight = 0L;
                while (!UpdateUtil.shouldTerminate()) {

                    long loopStartTimestamp = System.currentTimeMillis();

                    try {
                        // This thread is only sending UDP messages, so it does not use MessageQueue.

                        // The last block of a voting window has an extended delay to allow a new verifier to join. If
                        // this is an eligible window, and blocks have not yet been sent, send a new block for each
                        // managed out-of-cycle verifier.
                        long height = frozenEdge.getBlockHeight() + 1;
                        if (height % 50 == 49 && height > lastTransmissionHeight &&
                                height <= BlockManager.openEdgeHeight(false) &&
                                BlockManager.likelyAcceptingNewVerifiers()) {
                            lastTransmissionHeight = height;

                            for (ManagedVerifier verifier : verifierList) {
                                if (!BlockManager.verifierInCurrentCycle(ByteBuffer.wrap(verifier.getIdentifier())) &&
                                        verifier.hasPrivateKey()) {
                                    LogUtil.println("sending UDP block for " + verifier + " at height " + height);
                                    broadcastUdpBlockForNewVerifier(verifier);
                                }
                            }
                        }

                    } catch (Exception e) {
                        LogUtil.println("exception in new-verifier thread main loop: " + PrintUtil.printException(e));
                    }

                    // Ensure a minimum interval between iterations. This includes processing time, so the actual sleep
                    // time may be zero.
                    while (System.currentTimeMillis() < loopStartTimestamp + minimumLoopInterval) {
                        ThreadUtil.sleep(300L);
                    }
                }
            }
        }).start();
    }

    private static void broadcastUdpBlockForNewVerifier(ManagedVerifier verifier) {

        Block block = createNextBlock(frozenEdge, verifier);
        Message message = new Message(MessageType.MinimalBlock51, new MinimalBlock(block.getVerificationTimestamp(),
                block.getVerifierSignature()));
        message.sign(verifier.getSeed());
        for (Node node : combinedCycle()) {
            Message.sendUdp(node.getIpAddress(), MeshListener.standardPortUdp, message);
        }
    }

    private static void loadManagedVerifiers() {

        Path path = Paths.get(managedVerifiersFile.getAbsolutePath());
        try {
            List<String> contentsOfFile = Files.readAllLines(path);
            for (String line : contentsOfFile) {
                line = line.trim();
                int indexOfHash = line.indexOf("#");
                String comment = "";
                if (indexOfHash >= 0) {
                    comment = line.substring(indexOfHash + 1).trim();
                    line = line.substring(0, indexOfHash).trim();
                }
                ManagedVerifier verifier = ManagedVerifier.fromString(line);
                if (verifier != null) {
                    verifierMap.put(ByteBuffer.wrap(verifier.getIdentifier()), verifier);
                    verifierList.add(verifier);

                    // In there is a comment, add it to the nickname manager for display in the monitoring interface.
                    if (!comment.isEmpty()) {
                        NicknameManager.put(verifier.getIdentifier(), comment);
                    }

                    // Also populate the maps here to avoid having to check for null values.
                    ByteBuffer identifier = ByteBuffer.wrap(verifier.getIdentifier());
                    consecutiveSuccessfulBlockFetches.put(identifier, new AtomicInteger());
                    inFastFetchMode.put(identifier, false);
                }
            }
        } catch (Exception e) {
            System.out.println("issue getting managed verifiers: " + PrintUtil.printException(e));
        }

        // Display the verifiers that were loaded into the list.
        for (ManagedVerifier verifier : verifierList) {
            System.out.println("got managed verifier: " + verifier);
        }
    }

    private static boolean checkResponseIdentifier(Message message, ManagedVerifier verifier) {
        if (message != null) {
            // Set the response identifier. This is displayed in the interface if mismatched.
            verifier.setResponseIdentifier(message.getSourceNodeIdentifier());

            // Also log if the identifier is mismatched.
            if (!ByteUtil.arraysAreEqual(verifier.getIdentifier(), message.getSourceNodeIdentifier())) {
                LogUtil.println(NicknameManager.get(verifier.getIdentifier()) + " identifier mismatch: " +
                        ByteUtil.arrayAsStringWithDashes(verifier.getIdentifier()) + ", response identifier: " +
                        ByteUtil.arrayAsStringWithDashes(verifier.getResponseIdentifier()));
            }
        }

        return message != null && ByteUtil.arraysAreEqual(verifier.getIdentifier(), message.getSourceNodeIdentifier());
    }

    private static void sendWhitelistRequest(ManagedVerifier verifier) {
        // Get the IP address of this sentinel according to the managed verifier.
        Message ipRequest = new Message(MessageType.IpAddressRequest53, null, verifier.getSeed());
        Message.fetchTcp(verifier.getHost(), verifier.getPort(), ipRequest, new MessageCallback() {
            @Override
            public void responseReceived(Message message) {
                // If the response identifier is correct and the content type is correct, send the whitelist request.
                if (checkResponseIdentifier(message, verifier) &&
                        (message.getContent() instanceof IpAddressMessageObject)) {
                    IpAddressMessageObject ipAddress = (IpAddressMessageObject) message.getContent();
                    Message whitelistRequest = new Message(MessageType.WhitelistRequest424, ipAddress,
                            verifier.getSeed());
                    Message.fetchTcp(verifier.getHost(), verifier.getPort(), whitelistRequest, new MessageCallback() {
                        @Override
                        public void responseReceived(Message message) {
                            LogUtil.println("whitelist response from " + NicknameManager.get(verifier.getIdentifier()) +
                                    ": " + message);
                        }
                    });
                }
            }
        });
    }

    private static void updateMesh(ManagedVerifier verifier) {

        // Get the mesh.
        Message message = new Message(MessageType.MeshRequest15, null, verifier.getSeed());
        Message.fetchTcp(verifier.getHost(), verifier.getPort(), message, new MessageCallback() {
            @Override
            public void responseReceived(Message message) {
                // If the response identifier is correct and the content type is correct, process the response.
                if (checkResponseIdentifier(message, verifier) && (message.getContent() instanceof MeshResponse)) {
                    MeshResponse response = (MeshResponse) message.getContent();
                    if (!response.getMesh().isEmpty()) {
                        verifierIdentifierToMeshMap.put(ByteBuffer.wrap(message.getSourceNodeIdentifier()),
                                response.getMesh());
                    }
                }
            }
        });
    }

    private static void updateBlocks(ManagedVerifier verifier) {

        ByteBuffer identifier = ByteBuffer.wrap(verifier.getIdentifier());

        // Get the next block in normal mode and the next 10 blocks in fast-fetch mode.
        long startHeightToFetch = BlockManager.getFrozenEdgeHeight() + 1L;
        long endHeightToFetch = startHeightToFetch + (inFastFetchMode.getOrDefault(identifier, false) ? 9 : 0);
        List<Block> blockList = new ArrayList<>();
        Message message = new Message(MessageType.BlockRequest11, new BlockRequest(startHeightToFetch, endHeightToFetch,
                false), verifier.getSeed());

        AtomicBoolean processedResponse = new AtomicBoolean(false);
        Message.fetchTcp(verifier.getHost(), verifier.getPort(), message, new MessageCallback() {
            @Override
            public void responseReceived(Message message) {

                // If the response identifier is correct and the content type is correct, process the response.
                int result;
                if (checkResponseIdentifier(message, verifier) && (message.getContent() instanceof BlockResponse)) {
                    BlockResponse blockResponse = (BlockResponse) message.getContent();
                    List<Block> blocks = blockResponse.getBlocks();
                    if (blocks.size() == 0) {
                        result = 0;
                    } else if (blocks.get(0).getBlockHeight() == startHeightToFetch &&
                            blocks.get(blocks.size() - 1).getBlockHeight() == endHeightToFetch) {
                        blockList.addAll(blocks);
                        result = blockList.size();
                    } else {
                        result = ManagedVerifier.queryResultErrorValue;
                    }
                } else {
                    result = ManagedVerifier.queryResultErrorValue;
                }

                // Log the result and mark the response as processed.
                verifier.logResult(result);
                processedResponse.set(true);
            }
        });

        while (!processedResponse.get()) {
            ThreadUtil.sleep(300L);
        }

        // If we obtained a block, freeze it.
        if (!blockList.isEmpty()) {
            for (Block block : blockList) {
                if (block.signatureIsValid()) {
                    freezeBlock(block);
                }
            }
            lastBlockReceivedTimestamp.set(System.currentTimeMillis());

            // Perform maintenance on the unfrozen block manager. Blocks are automatically registered with the manager
            // in block requests, so they must be periodically removed to prevent problems.
            UnfrozenBlockManager.performMaintenance();

            // Four consecutive successes activate fast-fetch mode unless we are very close to the open edge. The
            // interval between fetches is less than the block duration, so multiple consecutive successful fetches
            // typically indicate that we need to catch up.
            if (consecutiveSuccessfulBlockFetches.get(identifier).incrementAndGet() >= 4 &&
                    BlockManager.getFrozenEdgeHeight() < BlockManager.openEdgeHeight(false) - 10) {
                if (!inFastFetchMode.get(identifier)) {
                    inFastFetchMode.put(identifier, true);
                    System.out.println("***** fast-fetch mode activated *****");
                }
            }
        } else {
            // Two consecutive failures deactivate fast-fetch mode.
            if (consecutiveSuccessfulBlockFetches.get(identifier).get() == 0) {
                if (inFastFetchMode.get(identifier)) {
                    inFastFetchMode.put(identifier, false);
                    System.out.println("***** fast-fetch mode deactivated *****");
                }
            } else {
                consecutiveSuccessfulBlockFetches.get(identifier).set(0);
            }
        }
    }

    private static void transmitBlockIfNecessary() {

        long frozenEdgeHeight = frozenEdge.getBlockHeight();
        long heightToProcess = frozenEdgeHeight + 1L;

        // The block creation delay prevents unnecessary work and unnecessary transmissions to the mesh when the
        // sentinel is initializing. We also allow the condition to be entered at least once to confirm that the
        // sentinel is able to calculate valid chain scores.
        if (!calculatingValidChainScores ||
                lastBlockReceivedTimestamp.get() < System.currentTimeMillis() - blockCreationDelay) {

            // This loop is fully serial, so there is no concern about threading issues. Check if the blocks
            // created are for the height to process. If not, clear them out.
            if (!blocksCreatedForManagedVerifiers.isEmpty()) {
                Block block = blocksCreatedForManagedVerifiers.iterator().next();
                if (block.getBlockHeight() != heightToProcess) {
                    blocksCreatedForManagedVerifiers.clear();
                }
            }

            // If the map of blocks is empty, make the blocks now.
            if (blocksCreatedForManagedVerifiers.isEmpty()) {
                for (ManagedVerifier verifier : verifierList) {
                    if (verifier.hasPrivateKey()) {
                        Block block = createNextBlock(frozenEdge, verifier);
                        if (block != null) {
                            blocksCreatedForManagedVerifiers.add(block);
                        }
                    }
                }
            }

            // If we have not yet transmitted a block for a managed verifier, check the scores for all blocks at this
            // height to see if one is suitable for transmission. There is no reasonable case where the sentinel should
            // transmit more than one block per height, so we only consider transmitting the block with the lowest
            // score.
            if (lastBlockTransmissionHeight < heightToProcess) {

                Block lowestScoredBlock = null;
                long lowestChainScore = Long.MAX_VALUE;
                for (Block block : blocksCreatedForManagedVerifiers) {
                    long chainScore = block.chainScore(frozenEdgeHeight);
                    if (BlockManager.verifierInCurrentCycle(ByteBuffer.wrap(block.getVerifierIdentifier())) &&
                            chainScore < lowestChainScore) {
                        lowestChainScore = chainScore;
                        lowestScoredBlock = block;
                    }
                }

                // If the block's minimum vote timestamp is in the past, transmit the block now. This is stricter
                // than the verifier, which will transmit a block whose minimum vote timestamp is up to 10 seconds
                // in the future.
                if (lowestChainScore < Long.MAX_VALUE - 1L) {

                    // Mark that we are able to create valid chain scores.
                    calculatingValidChainScores = true;

                    long minimumVoteTimestamp = frozenEdge.getVerificationTimestamp() +
                            Block.minimumVerificationInterval + lowestChainScore * 20000L + blockTransmissionDelay;

                    LogUtil.println(String.format("minimum vote timestamp is in %.1f seconds",
                            (minimumVoteTimestamp - System.currentTimeMillis()) / 1000.0) + " for chain score " +
                            lowestChainScore);

                    if (minimumVoteTimestamp < System.currentTimeMillis() && lastBlockTransmissionTimestamp <
                            System.currentTimeMillis() - minimumBlockTransmissionInterval) {

                        lastBlockTransmissionTimestamp = System.currentTimeMillis();

                        // Make the message and resign with the appropriate verifier seed. The message must be signed
                        // by an in-cycle verifier to make it past the blacklist mechanism.
                        ManagedVerifier verifier =
                                verifierMap.get(ByteBuffer.wrap(lowestScoredBlock.getVerifierIdentifier()));
                        Message message = new Message(MessageType.NewBlock9, new NewBlockMessage(lowestScoredBlock),
                                verifier.getSeed());

                        Set<Node> combinedCycle = combinedCycle();
                        AtomicInteger responsesReceived = new AtomicInteger(0);
                        for (Node node : combinedCycle) {
                            Message.fetch(node, message, new MessageCallback() {
                                @Override
                                public void responseReceived(Message message) {
                                    if (message != null && message.getType() == MessageType.NewBlockResponse10) {
                                        responsesReceived.incrementAndGet();
                                    }
                                }
                            });
                        }
                        lastBlockTransmissionHeight = lowestScoredBlock.getBlockHeight();
                        lastBlockTransmissionString = lowestScoredBlock.toString();
                        PersistentData.put(lastBlockTransmissionHeightKey, lastBlockTransmissionHeight);
                        PersistentData.put(lastBlockTransmissionStringKey, lastBlockTransmissionString);
                        LogUtil.println(ConsoleColor.Yellow.background() + "sent block for " +
                                PrintUtil.compactPrintByteArray(lowestScoredBlock.getVerifierIdentifier()) +
                                " with hash " + PrintUtil.compactPrintByteArray(lowestScoredBlock.getHash()) +
                                " at height " + lowestScoredBlock.getBlockHeight() + ConsoleColor.reset);

                        // Wait 3 seconds for the responses and store the results for display. This is done to ensure
                        // that any problems that affect delivery of blocks, from network issues to bugs in the code,
                        // are detected promptly.
                        ThreadUtil.sleep(3000L);
                        int successes = responsesReceived.get();
                        int failures = combinedCycle.size() - successes;
                        lastBlockTransmissionResults = successes + " success, " + failures + " fail";
                        PersistentData.put(lastBlockTransmissionResultsKey, lastBlockTransmissionResults);
                        LogUtil.println(ConsoleColor.Yellow.background() + "transmission results: " +
                                lastBlockTransmissionResults + ConsoleColor.reset);
                    }
                }
            }
        }
    }

    private static Set<Node> combinedCycle() {

        Map<ByteBuffer, Node> ipAddressToNodeMap = new HashMap<>();
        for (List<Node> nodes : verifierIdentifierToMeshMap.values()) {
            for (Node node : nodes) {
                if (BlockManager.verifierInCurrentCycle(ByteBuffer.wrap(node.getIdentifier()))) {
                    ipAddressToNodeMap.put(ByteBuffer.wrap(node.getIpAddress()), node);
                }
            }
        }

        return new HashSet<>(ipAddressToNodeMap.values());
    }

    private static Block createNextBlock(Block previousBlock, ManagedVerifier verifier) {

        // This method is very similar to the createNextBlock method of the Verifier class. Some code duplication is
        // warranted in this case to allow flexibility in modifying this method for the sentinel's use without risking
        // the functionality of the verifier.

        Block block = null;
        if (previousBlock != null && !ByteUtil.arraysAreEqual(previousBlock.getVerifierIdentifier(),
                verifier.getIdentifier())) {

            // Determine the block height and make the transaction list.
            long blockHeight = previousBlock.getBlockHeight() + 1L;
            List<Transaction> transactions = new ArrayList<>();

            // Add the seed transaction, if one is available.
            Transaction seedTransaction = SeedTransactionManager.transactionForBlock(blockHeight);
            if (seedTransaction != null) {
                transactions.add(seedTransaction);
            }

            // If the verifier is supposed to add a sentinel transaction, add it now. This is a 1-micronyzo transaction
            // from the sender to a dead wallet (all zeros). The minimum transaction fee is 1 micronyzo, so no funds
            // will be transferred. The only purpose of this transaction is to add metadata to the blockchain.
            // Out-of-cycle verifiers do not add sentinel transactions, as these transactions would complicate the
            // block-rebuilding process.
            if (verifier.isSentinelTransactionEnabled() &&
                    BlockManager.verifierInCurrentCycle(ByteBuffer.wrap(verifier.getIdentifier()))) {
                BalanceList balanceList = BalanceListManager.balanceListForBlock(previousBlock);
                if (balanceList == null) {
                    LogUtil.println("omitting sentinel transaction due to unavailable balance list");
                } else {
                    // Only add the sentinel transaction if the balance is over the minimum preferred balance.
                    Map<ByteBuffer, Long> balanceMap = BalanceManager.makeBalanceMap(balanceList);
                    long verifierBalance = balanceMap.getOrDefault(ByteBuffer.wrap(verifier.getIdentifier()), 0L);
                    if (verifierBalance <= BalanceManager.minimumPreferredBalance) {
                        LogUtil.println("omitting sentinel transaction because balance of " +
                                PrintUtil.compactPrintByteArray(verifier.getIdentifier()) + " is " +
                                PrintUtil.printAmount(verifierBalance) + ", which is less than minimum preferred " +
                                "balance of " + PrintUtil.printAmount(BalanceManager.minimumPreferredBalance));
                    } else {
                        long timestamp = previousBlock.getStartTimestamp() + Block.blockDuration + 1L;
                        long amount = 1L;
                        byte[] receiverIdentifier = new byte[FieldByteSize.identifier];
                        long previousHashHeight = previousBlock.getBlockHeight();
                        byte[] previousBlockHash = previousBlock.getHash();
                        String dataString = "block from sentinel v" + Version.getVersion();
                        byte[] senderData = dataString.getBytes(StandardCharsets.UTF_8);
                        Transaction sentinelTransaction = Transaction.standardTransaction(timestamp, amount,
                                receiverIdentifier, previousHashHeight, previousBlockHash, senderData,
                                verifier.getSeed());
                        transactions.add(sentinelTransaction);
                    }
                }
            }

            List<Transaction> approvedTransactions = BalanceManager.approvedTransactionsForBlock(transactions,
                    previousBlock, true);

            BalanceList previousBalanceList = BalanceListManager.balanceListForBlock(previousBlock);
            BalanceList balanceList = Block.balanceListForNextBlock(previousBlock, previousBalanceList,
                    approvedTransactions, verifier.getIdentifier(), previousBlock.getBlockchainVersion());
            if (balanceList != null) {
                long startTimestamp = BlockManager.startTimestampForHeight(blockHeight);
                block = new Block(previousBlock.getBlockchainVersion(), blockHeight, previousBlock.getHash(),
                        startTimestamp, approvedTransactions, balanceList.getHash(), verifier.getSeed());
            }
        }


        return block;
    }

    private static synchronized void freezeBlock(Block block) {

        numberOfBlocksReceived++;
        if (block.getBlockHeight() == BlockManager.getFrozenEdgeHeight() + 1L) {
            numberOfBlocksFrozen++;
            BlockManager.freezeBlock(block);
            frozenEdge = block;

            System.out.println("froze block " + block + String.format(", efficiency: %.1f%%", getEfficiency()));
        }
    }

    public static double getEfficiency() {
        return numberOfBlocksFrozen * 100.0 / Math.max(1.0, numberOfBlocksReceived);
    }

    public static List<ManagedVerifier> getManagedVerifiers() {
        if (!loadedManagedVerifiers.getAndSet(true)) {
            loadManagedVerifiers();
        }
        return verifierList;
    }

    public static boolean isCalculatingValidChainScores() {
        return calculatingValidChainScores;
    }

    public static long getLastBlockTransmissionHeight() {
        return lastBlockTransmissionHeight;
    }

    public static String getLastBlockTransmissionString() {
        return lastBlockTransmissionString;
    }

    public static String getLastBlockTransmissionResults() {
        return lastBlockTransmissionResults;
    }
}
