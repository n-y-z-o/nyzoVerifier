package co.nyzo.verifier;

import co.nyzo.verifier.util.PreferencesUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoricalChainFiller {

    private static final String blockBaseUrlKey = "consolidated_block_base_url";
    private static final String blockBaseUrl = PreferencesUtil.get(blockBaseUrlKey,
            "https://nyzo-blocks.nyc3.digitaloceanspaces.com/");
    private static ChainSectionRetriever gapRetriever = null;

    public static void fillChainHistory() {

        // Step back from the frozen edge to find the latest block that is missing.
        Block block = BlockManager.getFrozenEdge();
        long gapEndHeight = -1L;
        while (gapEndHeight < 0L) {
            Block previousBlock = BlockManager.frozenBlockForHeight(block.getBlockHeight() - 1L);
            if (previousBlock == null) {
                gapEndHeight = block.getBlockHeight() - 1L;
            } else {
                block = previousBlock;
            }
        }

        // If a height was found, try to get the consolidated file that will fill it in.
        if (gapEndHeight > 0 && gapEndHeight > BlockManager.getTrailingEdgeHeight()) {
            long fileIndex = gapEndHeight / BlockManager.blocksPerFile;
            long frozenEdgeFileIndex = BlockManager.getFrozenEdgeHeight() / BlockManager.blocksPerFile;

            // If the file index is the frozen-edge index, the previous file needs to be fetched, instead, and the gap
            // needs to be fetched from the cycle.
            boolean fetchFile;
            List<Block> gapBlocks;
            if (fileIndex < frozenEdgeFileIndex) {
                fetchFile = true;
                gapBlocks = new ArrayList<>();
            } else {  // fileIndex == frozenEdgeFileIndex
                fileIndex--;  // step back to the previous file, which is the latest file available
                long gapStartHeight = frozenEdgeFileIndex * BlockManager.blocksPerFile;

                // Get the gap retriever. If a new one needs to be made, make it now.
                ChainSectionRetriever gapRetriever = HistoricalChainFiller.gapRetriever;
                if (gapRetriever == null || gapRetriever.getStartHeight() != gapStartHeight ||
                        gapRetriever.getEndHeight() != gapEndHeight) {
                    gapRetriever = new ChainSectionRetriever(gapStartHeight, gapEndHeight,
                            block.getPreviousBlockHash());
                }

                // Get the blocks from the gap retriever. If the blocks in the gap are available, then the file can be
                // fetched.
                boolean gapRetrieverComplete = gapRetriever.isComplete();  // Access before the blocks to avoid a race.
                gapBlocks = gapRetriever.getBlocks();
                if (gapBlocks.size() == gapEndHeight - gapStartHeight + 1) {
                    fetchFile = true;
                } else {
                    // In this case, the file cannot be fetched yet. If the gap retriever has completed, it is set to
                    // null so that a new one can be made to attempt the process again.
                    fetchFile = false;
                    if (gapRetrieverComplete) {
                        gapRetriever = null;
                    }
                }

                // Store the gap retriever in the class field. The local field was used throughout the loop to avoid
                // any question of concurrency issues.
                HistoricalChainFiller.gapRetriever = gapRetriever;
            }

            if (fetchFile) {
                requestFile(fileIndex, gapBlocks, block.getPreviousBlockHash());
            }
        }
    }

    private static void requestFile(long fileIndex, List<Block> gapBlocks, byte[] anchorHash) {
        File file = BlockManager.consolidatedFileForBlockHeight(fileIndex * BlockManager.blocksPerFile);

        // Ensure that the consolidated directory exists.
        file.getParentFile().mkdirs();

        try {
            // Download the file to a temporary location.
            URL url = new URL(blockBaseUrl + file.getName());
            ReadableByteChannel channel = Channels.newChannel(url.openStream());
            File temporaryFile = new File(file.getAbsolutePath() + "_temp");
            temporaryFile.delete();
            FileOutputStream outputStream = new FileOutputStream(temporaryFile);
            outputStream.getChannel().transferFrom(channel, 0, Long.MAX_VALUE);
            channel.close();
            outputStream.close();

            // Load the blocks of the file into memory and add the gap blocks to the end of the list.
            long minimumHeight = fileIndex * BlockManager.blocksPerFile;
            long maximumHeight = minimumHeight + BlockManager.blocksPerFile - 1;
            List<Block> blocks = BlockManager.loadBlocksInFile(temporaryFile, minimumHeight, maximumHeight);
            blocks.addAll(gapBlocks);

            // Step back to the beginning of the file to check for blockchain continuity.
            boolean allAreGood = true;
            byte[] expectedHash = anchorHash;
            for (int i = blocks.size() - 1; i >= 0 && allAreGood; i--) {
                Block block = blocks.get(i);
                if (ByteUtil.arraysAreEqual(expectedHash, block.getHash())) {
                    expectedHash = block.getPreviousBlockHash();
                } else {
                    allAreGood = false;
                }
            }

            // If all the blocks are continuous, move the consolidated file to its permanent position and write the gap
            // blocks to file. Also, add the blocks to the block manager map.
            if (allAreGood) {
                // Move the consolidated file to its permanent location.
                file.delete();
                temporaryFile.renameTo(file);

                // Save individual block files for the gap blocks.
                BalanceList balanceList =
                        BlockManager.loadBalanceListFromFile(file, blocks.get(0).getBlockHeight());
                if (blocks.size() > BlockManager.blocksPerFile) {
                    for (int i = 1; i < blocks.size(); i++) {
                        balanceList = Block.balanceListForNextBlock(blocks.get(i - 1), balanceList,
                                blocks.get(i).getTransactions(), blocks.get(i).getVerifierIdentifier(),
                                blocks.get(i).getBlockchainVersion());

                        if (i >= BlockManager.blocksPerFile) {
                            BlockManager.writeBlocksToFile(Collections.singletonList(blocks.get(i)),
                                    Collections.singletonList(balanceList),
                                    BlockManager.individualFileForBlockHeight(blocks.get(i).getBlockHeight()));
                        }
                    }
                }

                // Add the blocks to the block manager map.
                for (Block block : blocks) {
                    BlockManagerMap.addBlock(block);
                }
            }

        } catch (Exception ignored) { }
    }
}
