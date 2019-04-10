package co.nyzo.verifier.sentinel;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.*;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.ThreadUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class Sentinel {

    private static final File managedVerifiersFile = new File(Verifier.dataRootDirectory, "managed_verifiers");

    private static final long meshUpdateInterval = 1000L * 60L * 5L;  // 5 minutes
    private static final long blockUpdateIntervalFast = 1000L;
    private static final long blockUpdateIntervalStandard = 2000L;
    private static final long minimumLoopInterval = 3000L;

    private static final long blockCreationDelay = 20000L;
    private static final long blockTransmissionDelay = 10000L;

    private static final Map<ByteBuffer, AtomicInteger> consecutiveSuccessfulBlockFetches = new ConcurrentHashMap<>();
    private static final Map<ByteBuffer, Boolean> inFastFetchMode = new ConcurrentHashMap<>();

    private static int numberOfBlocksReceived = 0;
    private static int numberOfBlocksFrozen = 0;

    private static final AtomicLong lastBlockReceivedTimestamp = new AtomicLong(0L);

    private static final Map<ByteBuffer, ManagedVerifier> verifiers = new ConcurrentHashMap<>();

    private static final Set<Block> blocksCreatedForManagedVerifiers = ConcurrentHashMap.newKeySet();
    private static boolean blockTransmittedForManagedVerifier = false;

    private static final Map<ByteBuffer, List<Node>> verifierIdentifierToMeshMap = new ConcurrentHashMap<>();

    private static Block frozenEdge = null;


    public static void main(String[] args) {

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
        for (ManagedVerifier verifier : verifiers.values()) {
            startThreadForVerifier(verifier, querySlot++);
        }

        // Start the thread for transmitting blocks. While a separate thread is needed for fetching data from each
        // managed verifier, only one thread is required for transmitting blocks, as only a single block at each height
        // will protect all verifiers, regardless of how many are down at that time.
        new Thread(() -> {
            // Set the last-block received timestamp so we do not immediately transmit a block.
            lastBlockReceivedTimestamp.set(System.currentTimeMillis());

            // Run the main loop.
            while (!UpdateUtil.shouldTerminate()) {
                transmitBlockIfNecessary();
                ThreadUtil.sleep(1000L);
            }
        }).start();
    }

    private static void startThreadForVerifier(ManagedVerifier verifier, int querySlot) {

        new Thread(() -> {

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
                int currentSlot = (int) ((System.currentTimeMillis() / blockUpdateInterval) % verifiers.size());
                if (lastBlockRequestedTimestamp < System.currentTimeMillis() - blockUpdateInterval &&
                        (currentSlot == querySlot ||
                                frozenEdge.getVerificationTimestamp() < System.currentTimeMillis() - 20000L)) {
                    lastBlockRequestedTimestamp = System.currentTimeMillis();
                    updateBlocks(verifier);
                }

                // Ensure a minimum interval between iterations. This includes processing time, so the actual sleep
                // time may be zero.
                while (System.currentTimeMillis() < loopStartTimestamp + minimumLoopInterval) {
                    ThreadUtil.sleep(300L);
                }
            }
        }).start();
    }

    private static void loadManagedVerifiers() {

        Path path = Paths.get(managedVerifiersFile.getAbsolutePath());
        try {
            List<String> contentsOfFile = Files.readAllLines(path);
            for (String line : contentsOfFile) {
                line = line.trim();
                int indexOfHash = line.indexOf("#");
                if (indexOfHash >= 0) {
                    line = line.substring(0, indexOfHash).trim();
                }
                ManagedVerifier verifier = ManagedVerifier.fromString(line);
                if (verifier != null) {
                    verifiers.put(ByteBuffer.wrap(verifier.getIdentifier()), verifier);

                    // Also populate the maps here to avoid having to check for null values.
                    ByteBuffer identifier = ByteBuffer.wrap(verifier.getIdentifier());
                    consecutiveSuccessfulBlockFetches.put(identifier, new AtomicInteger());
                    inFastFetchMode.put(identifier, false);
                }
            }
        } catch (Exception e) {
            System.out.println("issue getting managed verifiers: " + PrintUtil.printException(e));
        }

        // Display the verifiers that were loaded into the map.
        for (ManagedVerifier verifier : verifiers.values()) {
            System.out.println("got managed verifier: " + verifier);
        }
    }

    private static Set<BootstrapResponseV2> fetchBootstrapResponses() {

        // Try to get a bootstrap response from each managed node. While we fully trust every node we manage, some may
        // be behind, so we want to query as many as possible to ensure we start as close to the cycle frozen edge as
        // possible. Continue looping until at least one response is received.
        Set<BootstrapResponseV2> bootstrapResponses = ConcurrentHashMap.newKeySet();
        while (bootstrapResponses.isEmpty()) {

            AtomicInteger numberOfResponsesPending = new AtomicInteger(verifiers.size());
            for (ManagedVerifier verifier : verifiers.values()) {

                Message bootstrapRequest = new Message(MessageType.BootstrapRequestV2_35,
                        new BootstrapRequest(MeshListener.getPort()));
                Message.fetch(verifier.getHost(), verifier.getPort(), bootstrapRequest, message -> {

                    System.out.println("response from " + verifier.getHost() + " is " + message);
                    if (message != null && (message.getContent() instanceof BootstrapResponseV2)) {
                        bootstrapResponses.add((BootstrapResponseV2) message.getContent());
                    }
                    numberOfResponsesPending.decrementAndGet();
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
        // within four cycles of the local frozen edge, we fetch and freeze one block four cycles back.
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

            // Try to get the block at the cutoff height. This sends requests to every managed verifier, but only
            // one good response is needed.
            Set<Block> blocks = ConcurrentHashMap.newKeySet();
            Set<BalanceList> balanceLists = ConcurrentHashMap.newKeySet();
            Message message = new Message(MessageType.BlockRequest11, new BlockRequest(cutoffHeight, cutoffHeight,
                    true));
            AtomicInteger numberOfResponsesPending = new AtomicInteger(verifiers.size());
            for (ManagedVerifier verifier : verifiers.values()) {

                Message.fetch(verifier.getHost(), verifier.getPort(), message, message1 -> {

                    if (message1 != null) {
                        try {
                            BlockResponse blockResponse = (BlockResponse) message1.getContent();
                            Block block = blockResponse.getBlocks().get(0);
                            BalanceList balanceList = blockResponse.getInitialBalanceList();
                            if (block.getBlockHeight() == cutoffHeight &&
                                    balanceList.getBlockHeight() == cutoffHeight) {
                                blocks.add(block);
                                balanceLists.add(balanceList);
                            }
                        } catch (Exception ignored) { }
                    }

                    numberOfResponsesPending.decrementAndGet();
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
            for (ManagedVerifier verifier : verifiers.values()) {
                inFastFetchMode.put(ByteBuffer.wrap(verifier.getIdentifier()), true);
            }
        }

        return successful;
    }

    private static void updateMesh(ManagedVerifier verifier) {

        // Get the mesh.
        Message message = new Message(MessageType.MeshRequest15, null);
        Message.fetch(verifier.getHost(), 9444, message, message1 -> {

            try {
                if (message1 != null) {
                    MeshResponse response = (MeshResponse) message1.getContent();
                    if (!response.getMesh().isEmpty()) {
                        verifierIdentifierToMeshMap.put(ByteBuffer.wrap(message1.getSourceNodeIdentifier()),
                                response.getMesh());
                    }
                }
            } catch (Exception ignored) { }
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
        Message.fetch(verifier.getHost(), verifier.getPort(), message, message1 -> {

            if (message1 != null) {
                try {
                    BlockResponse blockResponse = (BlockResponse) message1.getContent();
                    List<Block> blocks = blockResponse.getBlocks();
                    if (blocks.size() > 0 && blocks.get(0).getBlockHeight() == startHeightToFetch &&
                    blocks.get(blocks.size() - 1).getBlockHeight() == endHeightToFetch) {
                        blockList.addAll(blocks);
                    }
                } catch (Exception ignored) { }
            }

            processedResponse.set(true);
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
                for (ManagedVerifier verifier : verifiers.values()) {
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
                    if (lowestScoredBlock == null || block.chainScore(frozenEdgeHeight) <
                            lowestScoredBlock.chainScore(frozenEdgeHeight)) {
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
                                verifiers.get(ByteBuffer.wrap(lowestScoredBlock.getVerifierIdentifier()));
                        message.sign(verifier.getSeed());

                        for (Node node : combinedCycle()) {
                            Message.fetch(IpUtil.addressAsString(node.getIpAddress()), node.getPort(), message,
                                    null);
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

            BalanceList previousBalanceList = BalanceListManager.balanceListForBlock(previousBlock, null);
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

            double efficiency = numberOfBlocksFrozen * 100.0 / numberOfBlocksReceived;
            System.out.println("froze block " + block + String.format(", efficiency: %.1f%%", efficiency));
        }
    }
}
