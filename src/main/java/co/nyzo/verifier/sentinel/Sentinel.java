package co.nyzo.verifier.sentinel;

import co.nyzo.verifier.*;
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

    private static final File managedVerifiersFile = new File(Verifier.dataRootDirectory, "managed_verifiers");

    private static final long meshUpdateInterval = 1000L * 60L * 5L;  // 5 minutes
    private static final long blockUpdateIntervalFast = 1000L;
    private static final long blockUpdateIntervalStandard = 2000L;
    private static final long minimumLoopInterval = 3000L;

    private static final long blockCreationDelay = 20000L;
    private static final long blockTransmissionDelay = 10000L;

    private static Map<ByteBuffer, AtomicInteger> consecutiveSuccessfulBlockFetches = new ConcurrentHashMap<>();
    private static Map<ByteBuffer, Boolean> inFastFetchMode = new ConcurrentHashMap<>();

    private static int numberOfBlocksReceived = 0;
    private static int numberOfBlocksFrozen = 0;

    private static AtomicLong lastBlockReceivedTimestamp = new AtomicLong(0L);

    private static final List<ManagedVerifier> verifierList = new CopyOnWriteArrayList<>();
    private static final Map<ByteBuffer, ManagedVerifier> verifierMap = new ConcurrentHashMap<>();

    private static final Set<Block> blocksCreatedForManagedVerifiers = ConcurrentHashMap.newKeySet();
    private static boolean blockTransmittedForManagedVerifier = false;

    private static final Map<ByteBuffer, List<Node>> verifierIdentifierToMeshMap = new ConcurrentHashMap<>();

    private static Block frozenEdge = null;


    public static void main(String[] args) {

        RunMode.setRunMode(RunMode.Sentinel);

        // If the preference is set, start the web listener.
        if (PreferencesUtil.getBoolean(WebListener.startWebListenerKey, false)) {
            WebListener.start();
        }

        SeedTransactionManager.start();
        start();
    }

    private static void start() {

        int loopCount = 0;

        Verifier.loadGenesisBlock();
        BlockFileConsolidator.start();

        // Load the managed verifiers. These are the verifiers for which the sentinel will produce blocks, if
        // necessary, and they are also used as data sources.
        loadManagedVerifiers();

        // Fetch and process the bootstrap response. This process will repeat until it is successful.
        boolean completedInitialization = false;
        while (!completedInitialization) {
            Set<BootstrapResponseV2> bootstrapResponses = fetchBootstrapResponses();
            System.out.println("got " + bootstrapResponses.size() + " bootstrap responses");
            completedInitialization = processBootstrapResponses(bootstrapResponses);
        }

        // Start a separate thread for fetching data from each of the managed verifiers. This may generate some
        // redundant work, but it will ensure that the sentinel continues to function properly even if a large segment
        // of the verifiers fail simultaneously.
        int querySlot = 0;
        for (ManagedVerifier verifier : verifierList) {
            startThreadForVerifier(verifier, querySlot++);
        }

        // Start a single thread as a fallback for fetching blocks from the full mesh if all managed verifiers become
        // unresponsive.
        startFullMeshThread();

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
                while (!UpdateUtil.shouldTerminate()) {

                    long loopStartTimestamp = System.currentTimeMillis();

                    // This is an important step, though one that should be used with care in a situation with many
                    // threads sending messages simultaneously. As all the individual verifier loops are invoking this
                    // method, none will monopolize the queue.
                    MessageQueue.blockThisThreadUntilClear();

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

    private static void startFullMeshThread() {

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

    private static Set<BootstrapResponseV2> fetchBootstrapResponses() {

        // Try to get a bootstrap response from each managed node. While we fully trust every node we manage, some may
        // be behind, so we want to query as many as possible to ensure we start as close to the cycle frozen edge as
        // possible. Continue looping until at least one response is received.
        Set<BootstrapResponseV2> bootstrapResponses = ConcurrentHashMap.newKeySet();
        while (bootstrapResponses.isEmpty()) {

            AtomicInteger numberOfResponsesPending = new AtomicInteger(verifierList.size());
            for (ManagedVerifier verifier : verifierList) {

                Message bootstrapRequest = new Message(MessageType.BootstrapRequestV2_35, new BootstrapRequest());
                Message.fetchTcp(verifier.getHost(), verifier.getPort(), bootstrapRequest, new MessageCallback() {
                    @Override
                    public void responseReceived(Message message) {

                        System.out.println("response from " + verifier.getHost() + " is " + message);
                        if (message != null && (message.getContent() instanceof BootstrapResponseV2)) {
                            bootstrapResponses.add((BootstrapResponseV2) message.getContent());
                        }
                        numberOfResponsesPending.decrementAndGet();
                    }
                });
            }

            // Wait for all responses to return.
            int waitIteration = 0;
            while (numberOfResponsesPending.get() > 0) {
                ThreadUtil.sleep(300L);
                System.out.println("after wait iteration " + waitIteration++ + ", " + numberOfResponsesPending.get() +
                        " bootstrap responses pending, have " + bootstrapResponses.size() + " good responses");
            }
        }

        return bootstrapResponses;
    }

    private static boolean processBootstrapResponses(Set<BootstrapResponseV2> bootstrapResponses) {

        boolean successful = false;

        System.out.println("processing bootstrap responses");

        // Get the local and chain frozen edges. If the chain is within four cycles of the local frozen edge, we do
        // not need to fetch anything here. The standard block-fetch process will get the chain. If the chain is not
        // within four cycles of the local frozen edge, we fetch and freeze one recent block.
        long localFrozenEdge = BlockManager.getFrozenEdgeHeight();
        long chainFrozenEdge = localFrozenEdge;
        List<ByteBuffer> cycleVerifiers = new ArrayList<>();
        for (BootstrapResponseV2 bootstrapResponse : bootstrapResponses) {
            if (bootstrapResponse.getFrozenEdgeHeight() > chainFrozenEdge) {
                chainFrozenEdge = bootstrapResponse.getFrozenEdgeHeight();
                cycleVerifiers = bootstrapResponse.getCycleVerifiers();
            }
        }

        long cutoffHeight = chainFrozenEdge - cycleVerifiers.size() * 4;
        if (localFrozenEdge >= cutoffHeight) {
            // If the frozen edge is close enough, nothing needs to be done. Flag processing as successful.
            frozenEdge = BlockManager.frozenBlockForHeight(localFrozenEdge);
            successful = true;
        } else {

            // Try to get the block at the chain frozen edge. This sends requests to every managed verifier, but only
            // one good response is needed.
            Set<Block> blocks = ConcurrentHashMap.newKeySet();
            Set<BalanceList> balanceLists = ConcurrentHashMap.newKeySet();
            long requestHeight = chainFrozenEdge;
            Message message = new Message(MessageType.BlockRequest11, new BlockRequest(requestHeight, requestHeight,
                    true));
            AtomicInteger numberOfResponsesPending = new AtomicInteger(verifierList.size());
            for (ManagedVerifier verifier : verifierList) {

                Message.fetchTcp(verifier.getHost(), verifier.getPort(), message, new MessageCallback() {
                    @Override
                    public void responseReceived(Message message) {

                        if (message != null) {
                            try {
                                BlockResponse blockResponse = (BlockResponse) message.getContent();
                                Block block = blockResponse.getBlocks().get(0);
                                BalanceList balanceList = blockResponse.getInitialBalanceList();
                                if (block.getBlockHeight() == requestHeight &&
                                        balanceList.getBlockHeight() == requestHeight) {
                                    blocks.add(block);
                                    balanceLists.add(balanceList);
                                }
                            } catch (Exception ignored) { }
                        }

                        numberOfResponsesPending.decrementAndGet();
                    }
                });
            }

            // Wait for all responses to return.
            int waitIteration = 0;
            while (numberOfResponsesPending.get() > 0) {
                ThreadUtil.sleep(300L);
                System.out.println("after wait iteration " + waitIteration++ + ", " + numberOfResponsesPending.get() +
                        " block responses pending");
            }

            // If a block was obtained, freeze it and flag that we have successfully processed the bootstrap response.
            if (!blocks.isEmpty() && !balanceLists.isEmpty()) {

                Block block = blocks.iterator().next();
                BalanceList balanceList = balanceLists.iterator().next();
                BlockManager.freezeBlock(block, block.getPreviousBlockHash(), balanceList, cycleVerifiers);
                frozenEdge = block;

                successful = true;

                // This timestamp is set whenever we receive a block so we do not try to create a block too soon
                // afterwards.
                lastBlockReceivedTimestamp.set(System.currentTimeMillis());
            }
        }

        // If we know that we are more than 20 blocks behind the frozen edge, start in fast-fetch mode.
        if (BlockManager.getFrozenEdgeHeight() < chainFrozenEdge - 20) {
            for (ManagedVerifier verifier : verifierList) {
                inFastFetchMode.put(ByteBuffer.wrap(verifier.getIdentifier()), true);
            }
        }

        return successful;
    }

    private static void updateMesh(ManagedVerifier verifier) {

        // Get the mesh.
        Message message = new Message(MessageType.MeshRequest15, null);
        Message.fetchTcp(verifier.getHost(), verifier.getPort(), message, new MessageCallback() {
            @Override
            public void responseReceived(Message message) {

                try {
                    if (message != null) {
                        MeshResponse response = (MeshResponse) message.getContent();
                        if (!response.getMesh().isEmpty()) {
                            verifierIdentifierToMeshMap.put(ByteBuffer.wrap(message.getSourceNodeIdentifier()),
                                    response.getMesh());
                        }
                    }
                } catch (Exception ignored) { }
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
                false));

        AtomicBoolean processedResponse = new AtomicBoolean(false);
        Message.fetchTcp(verifier.getHost(), verifier.getPort(), message, new MessageCallback() {
            @Override
            public void responseReceived(Message message) {

                int result;
                if (message == null) {
                    result = ManagedVerifier.queryResultErrorValue;
                } else {
                    try {
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
                    } catch (Exception ignored) {
                        result = ManagedVerifier.queryResultErrorValue;
                    }
                }
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
                freezeBlock(block);
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
        // sentinel is initializing.
        if (lastBlockReceivedTimestamp.get() < System.currentTimeMillis() - blockCreationDelay) {

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
                    Block block = createNextBlock(frozenEdge, verifier);
                    if (block != null) {
                        blocksCreatedForManagedVerifiers.add(block);
                    }
                }

                // Flag that we have not yet transmitted a block for the managed verifier.
                blockTransmittedForManagedVerifier = false;
            }

            // If we have not yet transmitted a block for the managed verifier, check the scores for all blocks at
            // this height to see if one is suitable for transmission. There is no reasonable case where the
            // sentinel should transmit more than one block per height, so we only consider transmitting the block
            // with the lowest score.
            if (!blockTransmittedForManagedVerifier) {

                Block lowestScoredBlock = null;
                for (Block block : blocksCreatedForManagedVerifiers) {
                    if (BlockManager.verifierInCurrentCycle(ByteBuffer.wrap(block.getVerifierIdentifier())) &&
                            (lowestScoredBlock == null || block.chainScore(frozenEdgeHeight) <
                            lowestScoredBlock.chainScore(frozenEdgeHeight))) {
                        lowestScoredBlock = block;
                    }
                }

                // If the block's minimum vote timestamp is in the past, transmit the block now. This is stricter
                // than the verifier, which will transmit a block whose minimum vote timestamp is up to 10 seconds
                // in the future.
                if (lowestScoredBlock != null) {

                    long minimumVoteTimestamp = frozenEdge.getVerificationTimestamp() +
                            Block.minimumVerificationInterval +
                            lowestScoredBlock.chainScore(frozenEdgeHeight) * 20000L + blockTransmissionDelay;

                    System.out.println(String.format("minimum vote timestamp is in %.1f seconds",
                            (minimumVoteTimestamp - System.currentTimeMillis()) / 1000.0));

                    if (minimumVoteTimestamp < System.currentTimeMillis()) {

                        // Make the message and resign with the appropriate verifier seed. The message must be signed
                        // by an in-cycle verifier to make it past the blacklist mechanism.
                        Message message = new Message(MessageType.NewBlock9, new NewBlockMessage(lowestScoredBlock));
                        ManagedVerifier verifier =
                                verifierMap.get(ByteBuffer.wrap(lowestScoredBlock.getVerifierIdentifier()));
                        message.sign(verifier.getSeed());

                        for (Node node : combinedCycle()) {
                            Message.fetch(node, message, null);
                        }
                        blockTransmittedForManagedVerifier = true;
                        System.out.println("sent block for " +
                                PrintUtil.compactPrintByteArray(lowestScoredBlock.getVerifierIdentifier()) +
                                " with hash " + PrintUtil.compactPrintByteArray(lowestScoredBlock.getHash()) +
                                " at height " + lowestScoredBlock.getBlockHeight());
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

            // Get the transactions for the block. For now, this does
            long blockHeight = previousBlock.getBlockHeight() + 1L;
            List<Transaction> transactions = TransactionPool.transactionsForHeight(blockHeight);

            // Add the seed transaction, if one is available.
            Transaction seedTransaction = SeedTransactionManager.transactionForBlock(blockHeight);
            if (seedTransaction != null) {
                transactions.add(seedTransaction);
            }

            // If the verifier is supposed to add a sentinel transaction, add it now. This is a 1-micronyzo transaction
            // from the sender to a dead wallet (all zeros). The minimum transaction fee is 1 micronyzo, so no funds
            // will be transferred. The only purpose of this transaction is to add metadata to the blockchain.
            if (verifier.isSentinelTransactionEnabled()) {
                long timestamp = previousBlock.getStartTimestamp() + Block.blockDuration + 1L;
                long amount = 1L;
                byte[] receiverIdentifier = new byte[FieldByteSize.identifier];
                long previousHashHeight = previousBlock.getBlockHeight();
                byte[] previousBlockHash = previousBlock.getHash();
                String dataString = "block from sentinel v" + Version.getVersion();
                byte[] senderData = dataString.getBytes(StandardCharsets.UTF_8);
                Transaction sentinelTransaction = Transaction.standardTransaction(timestamp, amount, receiverIdentifier,
                        previousHashHeight, previousBlockHash, senderData, verifier.getSeed());
                transactions.add(sentinelTransaction);
            }

            List<Transaction> approvedTransactions = BalanceManager.approvedTransactionsForBlock(transactions,
                    previousBlock);

            BalanceList previousBalanceList = BalanceListManager.balanceListForBlock(previousBlock);
            BalanceList balanceList = Block.balanceListForNextBlock(previousBlock, previousBalanceList,
                    approvedTransactions, verifier.getIdentifier());
            if (balanceList != null) {
                long startTimestamp = BlockManager.startTimestampForHeight(blockHeight);
                block = new Block(blockHeight, previousBlock.getHash(), startTimestamp, approvedTransactions,
                        balanceList.getHash(), verifier.getSeed());
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
        return verifierList;
    }
}
