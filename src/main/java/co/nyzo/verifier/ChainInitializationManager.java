package co.nyzo.verifier;

import co.nyzo.verifier.messages.BlockRequest;
import co.nyzo.verifier.messages.BlockResponse;
import co.nyzo.verifier.messages.BootstrapResponseV2;
import co.nyzo.verifier.util.DebugUtil;
import co.nyzo.verifier.util.NotificationUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChainInitializationManager {

    // This is a map from the identifier to the bootstrap response.
    private static final Map<ByteBuffer, BootstrapResponseV2> bootstrapResponses = new HashMap<>();

    public static synchronized void processBootstrapResponseMessage(Message message) {

        BootstrapResponseV2 response = (BootstrapResponseV2) message.getContent();
        bootstrapResponses.put(ByteBuffer.wrap(message.getSourceNodeIdentifier()), response);
    }

    public static synchronized BootstrapResponseV2 winningResponse() {

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

    public static void fetchBlock(BootstrapResponseV2 bootstrapResponse) {

        Set<BalanceList> balanceListSet = new HashSet<>();  // single item; using a set to allow access from thread
        Set<Block> blockSet = new HashSet<>();  // single item; using a set to allow access from thread

        while (!UpdateUtil.shouldTerminate() && (balanceListSet.isEmpty() || blockSet.isEmpty())) {

            AtomicBoolean processedResponse = new AtomicBoolean(false);

            long height = bootstrapResponse.getFrozenEdgeHeight();
            Message message = new Message(MessageType.BlockRequest11, new BlockRequest(height, height, true));
            Message.fetchFromRandomNode(message, new MessageCallback() {
                @Override
                public void responseReceived(Message message) {

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
}
