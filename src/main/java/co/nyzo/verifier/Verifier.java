package co.nyzo.verifier;

import co.nyzo.verifier.util.SignatureUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class Verifier {

    private static final AtomicBoolean alive = new AtomicBoolean(false);
    private static byte[] privateSeed = null;

    public static void main(String[] args) {
        start();
    }

    public static boolean isAlive() {
        return alive.get();
    }

    private static void loadPrivateSeed() {

        final Path seedFile = Paths.get("/etc/nyzo/verifier_private_seed");
        try {
            List<String> lines = Files.readAllLines(seedFile);
            if (lines != null && !lines.isEmpty()) {
                String line = lines.get(0);
                if (line.length() > 64) {
                    System.out.println("line length is " + line.length());
                    privateSeed = ByteUtil.byteArrayFromHexString(lines.get(0), 32);
                }
            }
        } catch (Exception ignored) { }

        if (privateSeed == null || ByteUtil.isAllZeros(privateSeed)) {
            privateSeed = KeyUtil.generateSeed();
            try {
                Files.write(seedFile, Arrays.asList(ByteUtil.arrayAsStringWithDashes(privateSeed)));
            } catch (Exception e) {
                privateSeed = null;
            }
        }
    }

    public static void start() {

        if (!alive.getAndSet(true)) {

            loadPrivateSeed();
            NodeManager.fetchNodeList(0);
            MeshListener.start();

            // Start the proactive side of the verifier, initiating whatever actions are necessary to maintain the mesh
            // and build the blockchain.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    verifierMain();
                    alive.set(false);
                }
            }).start();
        }
    }

    private static void verifierMain() {

        while (!UpdateUtil.shouldTerminate()) {

            long sleepTime = 1000L;
            try {
                // Only run the active verifier if connected to the mesh and if a Genesis block is available.
                if (NodeManager.connectedToMesh() && BlockManager.highestBlockFrozen() >= 0) {

                    // Get all of the current chain options. Add one block for each that is not caught up with the
                    // highest block open for processing.
                    long highestBlockOpenForProcessing = BlockManager.highestBlockOpenForProcessing();
                    List<ChainOption> options = ChainOptionManager.currentOptions();
                    System.out.println("have " + options.size() + " chain option" + (options.size() == 1 ? "" : "s") +
                            " to extend");
                    for (ChainOption option : options) {
                        Block previousBlock = option.getHighestBlock();
                        if (previousBlock.getBlockHeight() < highestBlockOpenForProcessing) {
                            System.out.println("need to extend block " + previousBlock.getBlockHeight() + " to reach " +
                                highestBlockOpenForProcessing);

                            // Create the block.
                            Block nextBlock = createNextBlock(previousBlock);

                            // Register the block with the chain option manager.
                            if (nextBlock != null) {
                                ChainOptionManager.registerBlock(nextBlock);
                            }
                        }
                    }
                }

            } catch (Exception ignored) { }

            // Sleep for a short time to avoid consuming too much computational power.
            try {
                Thread.sleep(sleepTime);
            } catch (Exception ignored) { }
        }
    }

    private static Block createNextBlock(Block previousBlock) {

        // Get the transactions for the block.
        long blockHeight = previousBlock.getBlockHeight() + 1L;
        List<Transaction> transactions = TransactionPool.transactionsForBlock(blockHeight);

        // TODO: only create block if it is acceptable for this node to create it

        List<Transaction> approvedTransactions = BalanceManager.approvedTransactionsForBlock(transactions, blockHeight);

        BalanceList balanceList = Block.balanceListForNextBlock(previousBlock, approvedTransactions,
                Verifier.getIdentifier());
        long startTimestamp = BlockManager.startTimestampForHeight(blockHeight);
        Block block = new Block(blockHeight, previousBlock.getHash(), balanceList.getRolloverFees(), startTimestamp,
            approvedTransactions, HashUtil.doubleSHA256(balanceList.getBytes()), balanceList);

        return block;
    }

    public static byte[] getIdentifier() {

        return KeyUtil.identifierForSeed(privateSeed);
    }

    public static byte[] sign(byte[] bytesToSign) {

        return SignatureUtil.signBytes(bytesToSign, privateSeed);
    }
}
