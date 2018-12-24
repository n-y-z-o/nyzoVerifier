package co.nyzo.verifier.sentinel;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.*;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.ThreadUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Sentinel {

    private static final File managedVerifiersFile = new File(Verifier.dataRootDirectory, "managed_verifiers");

    private static final long meshUpdateInterval = 20000L;
    private static final long blockUpdateIntervalFast = 1000L;
    private static final long blockUpdateIntervalStandard = 2000L;

    private static final long blockTransmitDelay = 30000L;

    private static int consecutiveSuccessfulBlockFetches = 0;
    private static boolean inFastFetchMode = false;

    private static int meshUpdateIndex = 0;
    private static int blockUpdateIndex = 0;

    private static long lastMeshUpdateTimestamp = 0L;
    private static long lastBlockUpdateTimestamp = 0L;
    private static long lastBlockReceivedTimestamp = 0L;

    private static final List<ManagedVerifier> verifierList = new ArrayList<>();
    private static final Map<ByteBuffer, ManagedVerifier> verifierMap = new HashMap<>();

    private static final Set<Block> blocksCreatedForManagedVerifiers = new HashSet<>();
    private static boolean blockTransmittedForManagedVerifier = false;

    private static final Map<ByteBuffer, List<Node>> verifierIdentifierToMeshMap = new HashMap<>();

    private static final Random random = new Random();

    public static void main(String[] args) {

        SeedTransactionManager.start();
        startMainLoop();
    }

    private static void startMainLoop() {

        new Thread(new Runnable() {
            @Override
            public void run() {

                int loopCount = 0;

                Verifier.loadGenesisBlock();

                // Load the managed verifiers. These are the verifiers for which the sentinel will produce blocks, if
                // necessary, and they are also used as data sources.
                loadManagedVerifiers();

                // Fetch and process the bootstrap response. This process will repeat until it is successful.
                boolean completedInitialization = false;
                while (!completedInitialization) {
                    BootstrapResponseV2 bootstrapResponse = fetchBootstrapResponse();
                    System.out.println("bootstrap response is " + bootstrapResponse);
                    completedInitialization = processBootstrapResponse(bootstrapResponse);
                }

                // This is the application loop.
                while (!UpdateUtil.shouldTerminate()) {

                    long loopStartTimestamp = System.currentTimeMillis();

                    // Update the mesh. We need to know who the in-cycle nodes are in order to send out a block if one
                    // needs to be created.
                    if (lastMeshUpdateTimestamp < System.currentTimeMillis() - meshUpdateInterval) {
                        lastMeshUpdateTimestamp = System.currentTimeMillis();
                        updateMesh();
                    }

                    // Update the blocks.
                    long blockUpdateInterval = inFastFetchMode ? blockUpdateIntervalFast : blockUpdateIntervalStandard;
                    if (lastBlockUpdateTimestamp < System.currentTimeMillis() - blockUpdateInterval) {
                        lastBlockUpdateTimestamp = System.currentTimeMillis();
                        updateBlocks();
                    }

                    transmitBlockIfNecessary();

                    MessageQueue.blockThisThreadUntilClear();

                    // Ensure that the minimum interval between iterations is 1 second. This includes all processing
                    // time, so the actual sleep might be zero.
                    while (System.currentTimeMillis() < loopStartTimestamp + 1000L) {
                        ThreadUtil.sleep(300L);
                    }
                }
            }
        }).start();
    }

    private static void loadManagedVerifiers() {

        Path path = Paths.get(managedVerifiersFile.getAbsolutePath());
        List<ManagedVerifier> managedVerifiers = new ArrayList<>();
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
                    managedVerifiers.add(verifier);
                }
            }
        } catch (Exception e) {
            System.out.println("issue getting managed verifiers: " + PrintUtil.printException(e));
        }

        for (ManagedVerifier verifier : managedVerifiers) {

            System.out.println("got managed verifier: " + verifier);
            verifierList.add(verifier);
            verifierMap.put(ByteBuffer.wrap(verifier.getIdentifier()), verifier);
        }
    }

    private static BootstrapResponseV2 fetchBootstrapResponse() {

        // Get the bootstrap response from one node. We fully trust all the nodes that we manage in the
        // sentinel, so instead of the democratic process of the trusted entry points of the verifier, this
        // only requires a single response.
        Set<BootstrapResponseV2> bootstrapResponseSet = new HashSet<>();  // a set for threading
        int verifierIndex = 0;
        while (bootstrapResponseSet.isEmpty()) {

            ManagedVerifier verifier = verifierList.get(verifierIndex);
            verifierIndex = (verifierIndex + 1) % verifierList.size();

            System.out.println("sending bootstrap request to " + verifier.getHost());

            AtomicBoolean receivedResponse = new AtomicBoolean(false);

            Message bootstrapRequest = new Message(MessageType.BootstrapRequestV2_35,
                    new BootstrapRequest(MeshListener.getPort()));
            Message.fetch(verifier.getHost(), verifier.getPort(), bootstrapRequest, new MessageCallback() {
                @Override
                public void responseReceived(Message message) {

                    if (message != null) {
                        bootstrapResponseSet.add((BootstrapResponseV2) message.getContent());
                    }
                    receivedResponse.set(true);
                }
            });

            for (int i = 0; i < 10 && !receivedResponse.get(); i++) {
                System.out.println("waiting for bootstrap response from " + verifier.getHost());
                ThreadUtil.sleep(300L);
            }
        }

        // Return the only element of the set.
        return bootstrapResponseSet.iterator().next();
    }

    private static boolean processBootstrapResponse(BootstrapResponseV2 bootstrapResponse) {

        boolean successful = false;

        // Get the local and chain frozen edges. If the chain is within four cycles of the local frozen edge, we do
        // not need to fetch anything here. The standard block-fetch process will get the chain. If the chain is not
        // within four cycles of the local frozen edge, we fetch and freeze one block four cycles back.
        long localFrozenEdge = BlockManager.getFrozenEdgeHeight();
        long chainFrozenEdge = bootstrapResponse.getFrozenEdgeHeight();

        long cutoffHeight = chainFrozenEdge - bootstrapResponse.getCycleVerifiers().size() * 4;
        if (localFrozenEdge >= cutoffHeight) {
            // If the frozen edge is close enough, nothing needs to be done. Flag processing as successful.
            successful = true;
        } else {
            AtomicBoolean processedResponse = new AtomicBoolean(false);

            // Try to get the block at the cutoff height. This will, at most, query each verifier once. If it fails for
            // all, this method returns a false value, causing a new bootstrap response to be fetched again and this
            // process to be attempted again.
            Set<Block> blockAsSet = new HashSet<>();
            Set<BalanceList> balanceListAsSet = new HashSet<>();
            Message message = new Message(MessageType.BlockRequest11, new BlockRequest(cutoffHeight, cutoffHeight,
                    true));
            for (int i = 0; i < verifierList.size() && (blockAsSet.isEmpty() || balanceListAsSet.isEmpty()); i++) {

                ManagedVerifier verifier = verifierList.get(i);
                Message.fetch(verifier.getHost(), verifier.getPort(), message, new MessageCallback() {
                    @Override
                    public void responseReceived(Message message) {

                        if (message != null) {
                            try {
                                BlockResponse blockResponse = (BlockResponse) message.getContent();
                                Block block = blockResponse.getBlocks().get(0);
                                BalanceList balanceList = blockResponse.getInitialBalanceList();
                                if (block.getBlockHeight() == cutoffHeight &&
                                        balanceList.getBlockHeight() == cutoffHeight) {
                                    blockAsSet.add(block);
                                    balanceListAsSet.add(balanceList);
                                }
                            } catch (Exception ignored) { }
                        }

                        processedResponse.set(true);
                    }
                });

                while (!processedResponse.get()) {
                    ThreadUtil.sleep(300L);
                }
            }

            // If a block was obtained, freeze it and flag that we have successfully processed the bootstrap response.
            if (!blockAsSet.isEmpty() && !balanceListAsSet.isEmpty()) {

                Block block = blockAsSet.iterator().next();
                BalanceList balanceList = balanceListAsSet.iterator().next();
                BlockManager.freezeBlock(block, block.getPreviousBlockHash(), balanceList,
                        bootstrapResponse.getCycleVerifiers());

                successful = true;

                // This timestamp is set whenever we receive a block so we do not try to create a block too soon
                // afterwards.
                lastBlockReceivedTimestamp = System.currentTimeMillis();
            }
        }

        // If we know that we are more than 20 blocks behind the frozen edge, start in fast-fetch mode.
        if (BlockManager.getFrozenEdgeHeight() < bootstrapResponse.getFrozenEdgeHeight() - 20) {
            inFastFetchMode = true;
        }

        return successful;
    }

    private static void updateMesh() {

        // Get the verifier from the list and advance the static index for the next iteration.
        ManagedVerifier verifier = verifierList.get(meshUpdateIndex);
        meshUpdateIndex = (meshUpdateIndex + 1) % verifierList.size();

        // Get the mesh.
        Message message = new Message(MessageType.MeshRequest15, null);
        Message.fetch(verifier.getHost(), 9444, message, new MessageCallback() {
            @Override
            public void responseReceived(Message message) {

                try {
                    if (message != null) {
                        MeshResponse response = (MeshResponse) message.getContent();
                        if (!response.getMesh().isEmpty()) {
                            synchronized (Sentinel.class) {
                                verifierIdentifierToMeshMap.put(ByteBuffer.wrap(message.getSourceNodeIdentifier()),
                                        response.getMesh());
                            }
                        }
                    }
                } catch (Exception ignored) { }
            }
        });
    }

    private static void updateBlocks() {

        // Get the verifier from the list and advance the index.
        ManagedVerifier verifier = verifierList.get(blockUpdateIndex);
        blockUpdateIndex = (blockUpdateIndex + 1) % verifierList.size();

        // Get the next block in normal mode and the
        long startHeightToFetch = BlockManager.getFrozenEdgeHeight() + 1L;
        long endHeightToFetch = startHeightToFetch + (inFastFetchMode ? 9 : 0);
        List<Block> blockList = new ArrayList<>();
        Message message = new Message(MessageType.BlockRequest11, new BlockRequest(startHeightToFetch, endHeightToFetch,
                false));

        AtomicBoolean processedResponse = new AtomicBoolean(false);
        Message.fetch(verifier.getHost(), verifier.getPort(), message, new MessageCallback() {
            @Override
            public void responseReceived(Message message) {

                if (message != null) {
                    try {
                        BlockResponse blockResponse = (BlockResponse) message.getContent();
                        List<Block> blocks = blockResponse.getBlocks();
                        if (blocks.size() > 0 && blocks.get(0).getBlockHeight() == startHeightToFetch &&
                        blocks.get(blocks.size() - 1).getBlockHeight() == endHeightToFetch) {
                            blockList.addAll(blocks);
                        }
                    } catch (Exception ignored) { }
                }

                processedResponse.set(true);
            }
        });

        while (!processedResponse.get()) {
            ThreadUtil.sleep(300L);
        }

        // If we obtained a block, freeze it.
        if (!blockList.isEmpty()) {
            for (Block block : blockList) {
                System.out.println("freezing block " + block);
                BlockManager.freezeBlock(block);
            }
            lastBlockReceivedTimestamp = System.currentTimeMillis();

            // Four consecutive successes activate fast-fetch mode unless we are very close to the open edge. The
            // interval between fetches is less than the block duration, so multiple consecutive successful fetches
            // typically indicate that we need to catch up.
            consecutiveSuccessfulBlockFetches++;
            if (consecutiveSuccessfulBlockFetches >= 4 &&
                    BlockManager.getFrozenEdgeHeight() < BlockManager.openEdgeHeight(false) - 10) {
                if (!inFastFetchMode) {
                    System.out.println("***** fast-fetch mode activated *****");
                }
                inFastFetchMode = true;
            }
        } else {
            // Two consecutive failures deactivate fast-fetch mode.
            if (consecutiveSuccessfulBlockFetches == 0) {
                if (inFastFetchMode) {
                    System.out.println("***** fast-fetch mode deactivated *****");
                }
                inFastFetchMode = false;
            }

            consecutiveSuccessfulBlockFetches = 0;
        }
    }

    private static void transmitBlockIfNecessary() {

        // Check if the last new block was received too long ago. If so, make blocks for the managed verifiers and
        // transmit if the scores justify transmission.
        long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
        long heightToProcess = frozenEdgeHeight + 1L;
        Block frozenEdge = BlockManager.frozenBlockForHeight(frozenEdgeHeight);
        if (lastBlockReceivedTimestamp < System.currentTimeMillis() - blockTransmitDelay) {

            // This loop is fully serial, so there is no concern about threading issues. Check if the blocks
            // created are for the height to process. If not, clear them out.
            if (!blocksCreatedForManagedVerifiers.isEmpty()) {
                Block block = blocksCreatedForManagedVerifiers.iterator().next();
                if (block.getBlockHeight() != heightToProcess) {
                    blocksCreatedForManagedVerifiers.clear();
                }
            }

            // If the map of blocks is empty, make them now. We need not worry about the inefficiency of
            // creating all the blocks, as the circumstance of the frozen edge being verified more than 30 seconds
            // ago must be uncommon for the blockchain to continue to function.
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
                    if (lowestScoredBlock == null || block.chainScore(frozenEdgeHeight) <
                            lowestScoredBlock.chainScore(frozenEdgeHeight)) {
                        lowestScoredBlock = block;
                    }
                }

                // If the block's minimum vote timestamp is in the past, transmit the block now. This is stricter
                // than the verifier, which will transmit a block whose minimum vote timestamp is up to 10 seconds
                // in the future.
                if (lowestScoredBlock != null) {

                    long minimumVoteTimestamp = frozenEdge.getVerificationTimestamp() + Block.blockDuration +
                            Block.minimumVerificationInterval + lowestScoredBlock.chainScore(frozenEdgeHeight) * 20000L;

                    if (minimumVoteTimestamp < System.currentTimeMillis()) {

                        // Make the message and resign with the appropriate verifier seed. The message must be signed
                        // by an in-cycle verifier to make it past the blacklist mechanism.
                        Message message = new Message(MessageType.NewBlock9, new NewBlockMessage(lowestScoredBlock));
                        ManagedVerifier verifier =
                                verifierMap.get(ByteBuffer.wrap(lowestScoredBlock.getVerifierIdentifier()));
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

    private static synchronized Set<Node> combinedCycle() {

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
                byte[] senderData = "block produced by Nyzo sentinel".getBytes(StandardCharsets.UTF_8);
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
}
