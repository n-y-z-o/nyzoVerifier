package co.nyzo.verifier;

import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.messages.BlockRequest;
import co.nyzo.verifier.messages.BlockResponse;
import co.nyzo.verifier.util.LogUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.ThreadUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChainSectionRetriever {

    private long startHeight;
    private long endHeight;
    private byte[] anchorHash;
    private boolean complete;
    private Map<Long, Block> blockMap;

    public ChainSectionRetriever(long startHeight, long endHeight, byte[] anchorHash) {
        this.startHeight = startHeight;
        this.endHeight = endHeight;
        this.anchorHash = anchorHash;
        this.complete = false;
        this.blockMap = new ConcurrentHashMap<>();

        start();
    }

    public long getStartHeight() {
        return startHeight;
    }

    public long getEndHeight() {
        return endHeight;
    }

    public byte[] getAnchorHash() {
        return anchorHash;
    }

    public List<Block> getBlocks() {

        // Build the list from the map.
        List<Block> blocks = new ArrayList<>();
        byte[] expectedHash = anchorHash;
        for (long height = endHeight; height >= startHeight; height--) {
            Block block = blockMap.get(height);
            if (block != null && ByteUtil.arraysAreEqual(expectedHash, block.getHash())) {
                blocks.add(0, block);
                expectedHash = block.getPreviousBlockHash();
            }
        }

        return blocks;
    }

    public boolean isComplete() {
        return complete;
    }

    private void start() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                int numberOfIterations = 0;
                final int maximumIterations = 1000;
                while (numberOfIterations++ < maximumIterations && !complete) {

                    // Wait for the message queue to clear. This ensures that the previous response has been dispatched
                    // before continuing.
                    MessageQueue.blockThisThreadUntilClear();

                    // If the trailing edge is set, gaps behind the frozen edge have been filled. If the section is
                    // behind the frozen edge, this retriever is no longer needed.
                    if (BlockManager.getTrailingEdgeHeight() > 0 && startHeight > 0 &&
                            startHeight < BlockManager.getFrozenEdgeHeight()) {
                        complete = true;
                    } else {

                        // If blocks are needed, fetch them.
                        byte[] expectedHash = anchorHash;
                        long fetchEndHeight = -1L;
                        for (long height = endHeight; height >= startHeight && fetchEndHeight < 0L; height--) {
                            Block block = blockMap.get(height);
                            if (block != null && ByteUtil.arraysAreEqual(expectedHash, block.getHash())) {
                                expectedHash = block.getPreviousBlockHash();
                            } else {
                                fetchEndHeight = height;
                            }
                        }
                        if (fetchEndHeight > 0) {
                            long fetchStartHeight = Math.max(startHeight, fetchEndHeight - 9);
                            BlockRequest request = new BlockRequest(fetchStartHeight, fetchEndHeight, false);
                            Message.fetchFromRandomNode(new Message(MessageType.BlockRequest11, request),
                                    new MessageCallback() {
                                        @Override
                                        public void responseReceived(Message message) {
                                            processFetchResponse(message);
                                        }
                                    });
                        } else {
                            complete = true;
                        }
                    }

                    // Sleep for 1 second to avoid consuming too much CPU.
                    if (numberOfIterations < maximumIterations && !complete) {
                        ThreadUtil.sleep(1000L);
                    }
                }

                complete = true;
            }
        }).start();
    }

    private void processFetchResponse(Message message) {

        if (message != null && message.getContent() instanceof BlockResponse) {
            BlockResponse response = (BlockResponse) message.getContent();
            List<Block> responseBlocks = response.getBlocks();
            if (!responseBlocks.isEmpty()) {

                // Get the end height of the list. Step back to that height, if it is valid.
                long listEndHeight = responseBlocks.get(responseBlocks.size() - 1).getBlockHeight();
                if (listEndHeight >= startHeight) {
                    byte[] expectedHash = anchorHash;
                    for (long height = endHeight; height > listEndHeight && expectedHash != null; height--) {
                        Block block = blockMap.get(height);
                        if (block != null && ByteUtil.arraysAreEqual(expectedHash, block.getHash())) {
                            expectedHash = block.getPreviousBlockHash();
                        } else {
                            expectedHash = null;
                        }
                    }

                    for (int i = responseBlocks.size() - 1; i >= 0 && expectedHash != null; i--) {
                        Block block = responseBlocks.get(i);
                        if (block.signatureIsValid() && ByteUtil.arraysAreEqual(expectedHash, block.getHash())) {
                            blockMap.put(block.getBlockHeight(), block);
                            expectedHash = block.getPreviousBlockHash();
                        } else {
                            expectedHash = null;
                        }
                    }
                }
            }
        }
    }
}
