package co.nyzo.verifier;

import co.nyzo.verifier.util.SignatureUtil;

import java.io.File;
import java.util.List;

public class Verifier {

    private static Wallet wallet;

    public static void main(String[] args) {
        start();
    }

    public static final File defaultWalletLocation = new File("/etc/nyzo/verifier_private_seed");

    public static Wallet getWallet() {
        return wallet;
    }

    public static void start() {

        // Setup the verifier wallet.
        Wallet wallet = Wallet.fromFile(defaultWalletLocation);
        System.out.println("wallet from file is: " + wallet);
        System.out.println("wallet location is: " + defaultWalletLocation.getAbsolutePath());
        if (wallet == null) {
            System.out.println("generating new wallet");
            wallet = Wallet.generateNew();
            wallet.toFile(defaultWalletLocation);
        }
        Verifier.wallet = wallet;

        // If we do not want to run a seed node, get the list of network nodes.
        if (!shouldRunAsSeed()) {
            NodeManager.fetchNodeList();
        }

        // Start the listener for incoming connections. This will be the reactive side of the verifier, responding
        // directly to network activity.
        MeshListener.start();

        // Start the proactive side of the verifier, initiating whatever actions are necessary to maintain the mesh and
        // build the blockchain.
        new Thread(new Runnable() {
            @Override
            public void run() {
                verifierMain();
            }
        }).start();
    }

    public static boolean shouldRunAsSeed() {

        return new File("/etc/nyzo/run_as_seed").exists();
    }

    private static void verifierMain() {

        while (true) {

            long sleepTime = 1000L;
            try {
                // Only run the active verifier if connected to the mesh or configured to start a new mesh.
                if (NodeManager.connectedToMesh() || shouldRunAsSeed()) {

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
        return wallet.getIdentifier();
    }

    public static byte[] sign(byte[] bytesToSign) {
        return SignatureUtil.signBytes(bytesToSign, wallet.getPrivateKey().getSeed());
    }
}
