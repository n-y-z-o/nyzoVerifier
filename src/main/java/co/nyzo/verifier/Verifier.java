package co.nyzo.verifier;

import co.nyzo.verifier.util.SignatureUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class Verifier {

    public static final File dataRootDirectory = new File("/var/lib/nyzo");

    private static final AtomicBoolean alive = new AtomicBoolean(false);
    private static byte[] privateSeed = null;

    static {
        loadPrivateSeed();
    }

    public static void main(String[] args) {

        if (args.length > 0) {
            try {
                List<String> arguments = new ArrayList<String>();
                for (int i = 1; i < args.length; i++) {
                    arguments.add(args[i]);
                }

                System.out.println("executing class " + args[0]);
                Class classToRun = Class.forName(args[0]);
                Method mainMethod = classToRun.getDeclaredMethod("main", String[].class);
                mainMethod.invoke(classToRun, new Object[]{arguments.toArray(new String[0])});
                System.out.println("fin.");
            } catch (Exception e) {

                System.out.println("unable to load class " + args[0]);
            }

        } else {
            start();
        }
    }

    public static boolean isAlive() {
        return alive.get();
    }

    private static void loadPrivateSeed() {

        dataRootDirectory.mkdirs();

        final Path seedFile = Paths.get(dataRootDirectory.getAbsolutePath() + "/verifier_private_seed");
        System.out.println("seed file path is " + seedFile);
        try {
            List<String> lines = Files.readAllLines(seedFile);
            if (lines != null && !lines.isEmpty()) {
                String line = lines.get(0);
                if (line.length() > 64) {
                    privateSeed = ByteUtil.byteArrayFromHexString(lines.get(0), 32);
                }
            }
        } catch (Exception ignored) { }

        if (privateSeed == null || ByteUtil.isAllZeros(privateSeed) || privateSeed.length != 32) {
            privateSeed = KeyUtil.generateSeed();
            try {
                Files.write(seedFile, Arrays.asList(ByteUtil.arrayAsStringWithDashes(privateSeed)));
            } catch (Exception e) {
                e.printStackTrace();
                privateSeed = null;
            }
        }
    }

    public static void start() {

        if (!alive.getAndSet(true)) {

            System.out.println("starting verifier");

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
            }, "Verifier-mainLoop").start();
        }
    }

    private static void verifierMain() {

        while (!UpdateUtil.shouldTerminate()) {

            long sleepTime = 10000L;
            try {
                // Only run the active verifier if connected to the mesh and if the block manager is ready.
                if (NodeManager.connectedToMesh() && BlockManager.readyToProcess()) {

                    long endHeight = ChainOptionManager.highestBlockRegistered();
                    long startHeight = endHeight - 2;
                    for (long height = startHeight; height <= endHeight; height++) {

                        // Try to extend the lowest-scoring block.
                        Block blockToExtend = ChainOptionManager.lowestScoredBlockForHeight(height);
                        if (blockToExtend != null) {
                            Block nextBlock = createNextBlock(blockToExtend);
                            boolean shouldTransmitBlock = ChainOptionManager.registerBlock(nextBlock);
                            if (shouldTransmitBlock) {
                                Message.broadcast(new Message(MessageType.NewBlock9, nextBlock));
                            }
                        }
                    }
                }

                System.out.println("connected to mesh: " + NodeManager.connectedToMesh() + "(" +
                        NodeManager.getMesh().size() + "), highest block frozen: " +
                        BlockManager.highestBlockFrozen() + ", ready to process: " + BlockManager.readyToProcess());

            } catch (Exception ignored) { }

            // Sleep for a short time to avoid consuming too much computational power.
            try {
                Thread.sleep(sleepTime);
            } catch (Exception ignored) { }
        }
    }

    private static Block createNextBlock(Block previousBlock) {

        Block block = null;
        if (previousBlock != null && !ByteUtil.arraysAreEqual(previousBlock.getVerifierIdentifier(),
                Verifier.getIdentifier())) {

            // Get the transactions for the block.
            long blockHeight = previousBlock.getBlockHeight() + 1L;
            List<Transaction> transactions = TransactionPool.transactionsForBlock(blockHeight);

            List<Transaction> approvedTransactions = BalanceManager.approvedTransactionsForBlock(transactions,
                    blockHeight);

            BalanceList balanceList = Block.balanceListForNextBlock(previousBlock, approvedTransactions,
                    Verifier.getIdentifier());
            long startTimestamp = BlockManager.startTimestampForHeight(blockHeight);
            block = new Block(blockHeight, previousBlock.getHash(), startTimestamp, approvedTransactions,
                    HashUtil.doubleSHA256(balanceList.getBytes()), balanceList);
        }

        return block;
    }

    public static byte[] getIdentifier() {

        return KeyUtil.identifierForSeed(privateSeed);
    }

    public static byte[] sign(byte[] bytesToSign) {

        return SignatureUtil.signBytes(bytesToSign, privateSeed);
    }
}
