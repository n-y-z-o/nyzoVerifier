package co.nyzo.verifier;

import co.nyzo.verifier.messages.BlockRequest;
import co.nyzo.verifier.messages.BlockResponse;
import co.nyzo.verifier.messages.BootstrapRequest;
import co.nyzo.verifier.messages.BootstrapResponseV2;
import co.nyzo.verifier.util.*;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ChainInitializationManager {

    // This is a map from the identifier to the bootstrap response.
    private static final Map<ByteBuffer, BootstrapResponseV2> bootstrapResponses = new ConcurrentHashMap<>();

    private static void processBootstrapResponseMessage(Message message) {

        BootstrapResponseV2 response = (BootstrapResponseV2) message.getContent();
        bootstrapResponses.put(ByteBuffer.wrap(message.getSourceNodeIdentifier()), response);
    }

    private static synchronized BootstrapResponseV2 winningResponse() {

        // Determine the response with the highest number of votes.
        Map<ByteBuffer, Integer> voteCounts = new HashMap<>();
        BootstrapResponseV2 winningResponse = null;
        int winningVoteCount = 0;
        for (BootstrapResponseV2 response : bootstrapResponses.values()) {

            // This is a simple count of each unique response. This considers all fields of the response to prevent
            // potential manipulation by omitting a cycle verifier.
            ByteBuffer responseBytes = ByteBuffer.wrap(response.getBytes());
            int voteCount = voteCounts.getOrDefault(responseBytes, 0) + 1;
            voteCounts.put(responseBytes, voteCount);

            // If this is the winning response so far, store it so we don't have to loop over the responses again.
            if (voteCount > winningVoteCount) {
                winningVoteCount = voteCount;
                winningResponse = response;
            }
        }

        // Return the winning response. In most cases, all votes will be the same, or votes will be split between two
        // frozen edges if the responses are built right around when a block is frozen.
        return winningResponse;
    }

    private static void fetchBlock(BootstrapResponseV2 bootstrapResponse) {

        Set<BalanceList> balanceListSet = new HashSet<>();  // single item; using a set to allow access from thread
        Set<Block> blockSet = new HashSet<>();  // single item; using a set to allow access from thread

        while (!UpdateUtil.shouldTerminate() && (balanceListSet.isEmpty() || blockSet.isEmpty())) {

            System.out.println("trying to fetch block for height " + bootstrapResponse.getFrozenEdgeHeight());

            AtomicBoolean processedResponse = new AtomicBoolean(false);

            long height = bootstrapResponse.getFrozenEdgeHeight();
            Message message = new Message(MessageType.BlockRequest11, new BlockRequest(height, height, true));
            Message.fetchFromRandomNode(message, new MessageCallback() {
                @Override
                public void responseReceived(Message message) {

                    System.out.println("received response for block fetch");

                    BlockResponse response = (BlockResponse) message.getContent();
                    List<Block> responseBlocks = response.getBlocks();

                    if (!responseBlocks.isEmpty() && response.getInitialBalanceList() != null) {

                        // If the hashes of the block and balance list are correct, they can be saved.
                        Block responseBlock = responseBlocks.get(0);
                        if (ByteUtil.arraysAreEqual(responseBlock.getHash(), bootstrapResponse.getFrozenEdgeHash()) &&
                                ByteUtil.arraysAreEqual(response.getInitialBalanceList().getHash(),
                                        responseBlock.getBalanceListHash())) {

                            blockSet.add(responseBlock);
                            balanceListSet.add(response.getInitialBalanceList());
                        }
                    }

                    processedResponse.set(true);
                }
            });

            // Sleep up to five seconds to allow the request to return.
            try {
                for (int i = 0; i < 10 && !processedResponse.get(); i++) {
                    Thread.sleep(500L);
                }
            } catch (Exception ignored) { }
        }

        if (!blockSet.isEmpty() && !balanceListSet.isEmpty()) {

            Block block = blockSet.iterator().next();
            BalanceList balanceList = balanceListSet.iterator().next();
            BlockManager.freezeBlock(block, block.getPreviousBlockHash(), balanceList,
                    bootstrapResponse.getCycleVerifiers());
        }
    }

    private static Map<Long, Block> blockMap(List<Block> blocks) {

        Map<Long, Block> map = new HashMap<>();
        for (Block block : blocks) {
            map.put(block.getBlockHeight(), block);
        }

        return map;
    }

    public static void initializeFrozenEdge(List<TrustedEntryPoint> trustedEntryPoints) {

        // Only continue with the edge-initialization process if we might need to fetch a new frozen edge. If the
        // open edge is fewer than 20 blocks ahead of the local frozen edge, and we have a complete cycle in the
        // block manager, we can treat the restart as a temporary outage and use standard recovery mechanisms.
        long openEdgeHeight = BlockManager.openEdgeHeight(false);
        if (BlockManager.isCycleComplete() && openEdgeHeight < BlockManager.getFrozenEdgeHeight() + 20) {
            System.out.println("skipping frozen-edge consensus process due to short downtime and complete cycle");
        } else {

            System.out.println("entering frozen-edge consensus process because open edge, " + openEdgeHeight +
                    ", is " + (openEdgeHeight - BlockManager.getFrozenEdgeHeight()) + " past frozen edge, " +
                    BlockManager.getFrozenEdgeHeight() + " and cycleComplete=" + BlockManager.isCycleComplete());

            // Attempt to jump into the blockchain. This should succeed on the first attempt, but it may take
            // longer if we are starting a new mesh.
            BootstrapResponseV2 consensusBootstrapResponse = null;
            while (consensusBootstrapResponse == null && !UpdateUtil.shouldTerminate()) {

                // Wait for the incoming message queue to clear. This will prevent a potential problem where this
                // loop continues to pile on more and more requests while not getting responses in time because the
                // queue is overfilled.
                MessageQueue.blockThisThreadUntilClear();

                AtomicInteger numberOfResponsesReceived = new AtomicInteger(0);

                // Send bootstrap requests to all trusted entry points.
                Message bootstrapRequest = new Message(MessageType.BootstrapRequestV2_35, new BootstrapRequest());
                for (TrustedEntryPoint entryPoint : trustedEntryPoints) {

                    System.out.println("sending Bootstrap request to " + entryPoint);
                    Message.fetchTcp(entryPoint.getHost(), entryPoint.getPort(), bootstrapRequest,
                            new MessageCallback() {
                                @Override
                                public void responseReceived(Message message) {
                                    if (message == null) {
                                        System.out.println("Bootstrap response is null");
                                    } else {
                                        numberOfResponsesReceived.incrementAndGet();
                                        processBootstrapResponseMessage(message);
                                    }
                                }
                            });
                }

                // Wait up to 5 seconds for requests to return.
                for (int i = 0; i < 20 && numberOfResponsesReceived.get() < trustedEntryPoints.size(); i++) {
                    ThreadUtil.sleep(250L);
                }

                // Get the consensus response. If this can be determined, we can move to the next step.
                consensusBootstrapResponse = winningResponse();
                System.out.println("consensus bootstrap response: " + consensusBootstrapResponse);
            }

            // If the consensus frozen edge is more than 20 past the local frozen edge, and we are not in the cycle,
            // fetch the consensus frozen edge. If the consensus frozen edge is more than the cycle length past the
            // frozen edge, it does not matter if we were in the cycle. We have lost our place, and the frozen edge
            // needs to be fetched. If we do not fetch the frozen edge here, the recovery mechanisms will attempt to
            // catch us back up in time to verify a block.
            if (consensusBootstrapResponse != null) {

                long consensusFrozenEdge = consensusBootstrapResponse.getFrozenEdgeHeight();
                boolean fetchRequiredNotInCycle = consensusFrozenEdge > BlockManager.getFrozenEdgeHeight() + 20 &&
                        !Verifier.inCycle();
                boolean fetchRequiredInCycle = consensusFrozenEdge > BlockManager.getFrozenEdgeHeight() +
                        BlockManager.currentCycleLength();

                System.out.println("local frozen edge: " + BlockManager.getFrozenEdgeHeight() +
                        ", consensus frozen edge: " + consensusFrozenEdge + ", fetch required (not-in-cycle): " +
                        fetchRequiredNotInCycle + ", fetch required (in-cycle): " + fetchRequiredInCycle);

                if (fetchRequiredNotInCycle || fetchRequiredInCycle || !BlockManager.isCycleComplete()) {
                    System.out.println("fetching block based on bootstrap response");
                    fetchBlock(consensusBootstrapResponse);
                }
            }
        }
    }
}
