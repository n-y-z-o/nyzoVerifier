package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.FileUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class GenesisBlockAcknowledgement implements MessageObject {

    private boolean blockAccepted;
    private String message;

    public GenesisBlockAcknowledgement(Block block) {

        StringBuilder error = new StringBuilder();
        if (Block.isValidGenesisBlock(block, error)) {
            blockAccepted = true;
            message = "Genesis block accepted";
            resetToGenesisBlock(block);
        } else {
            blockAccepted = false;
            message = "Genesis block not accepted (error=\"" + error + "\")";
        }
    }

    private GenesisBlockAcknowledgement(boolean blockAccepted, String message) {
        this.blockAccepted = blockAccepted;
        this.message = message;
    }

    public boolean isBlockAccepted() {
        return blockAccepted;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public int getByteSize() {
        return FieldByteSize.booleanField +       // blockAccepted
                FieldByteSize.string(message);    // message
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(blockAccepted ? (byte) 1 : (byte) 0);
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        buffer.putShort((short) messageBytes.length);
        buffer.put(messageBytes);

        return array;
    }

    public static GenesisBlockAcknowledgement fromByteBuffer(ByteBuffer buffer) {

        GenesisBlockAcknowledgement result = null;

        try {
            boolean blockAccepted = buffer.get() == 1;
            short messageByteLength = buffer.getShort();
            byte[] messageBytes = new byte[messageByteLength];
            buffer.get(messageBytes);
            String message = new String(messageBytes, StandardCharsets.UTF_8);

            result = new GenesisBlockAcknowledgement(blockAccepted, message);

        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    // Note for anyone reading old commits: this method was used to test live resetting to a new Genesis block to
    // ensure the system was operating properly before release. If you want to start a new blockchain based on this
    // codebase, we recommend doing something similar before you release publicly: get a small mesh running, and then
    // send a new genesis block to the mesh and start over a few times to make sure the verifier queue forms properly
    // at the beginning of the blockchain.
    private static void resetToGenesisBlock(Block genesisBlock) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                // Flag that the system should terminate and close the MeshListener socket.
                UpdateUtil.terminate();
                MeshListener.closeServerSocket();

                // Wait for the verifier and mesh listener to terminate.
                while (Verifier.isAlive() || MeshListener.isAlive()) {
                    try {
                        Thread.sleep(300L);
                        System.out.println("waiting for termination: v=" + Verifier.isAlive() + ", m=" +
                                MeshListener.isAlive());
                    } catch (Exception ignored) { }
                }

                // Delete the block directory and reset all classes that are storing state related to the current
                // blockchain.
                FileUtil.delete(BlockManager.blockRootDirectory);
                BlockManager.reset();
                BlockManagerMap.reset();
                Block.reset();
                TransactionPool.reset();

                // Freeze the Genesis block.
                genesisBlock.setBalanceList(Block.balanceListForNextBlock(null, genesisBlock.getTransactions(),
                        genesisBlock.getVerifierIdentifier()));
                BlockManager.freezeBlock(genesisBlock);

                // Exit the application.
                System.exit(0);
            }
        }).start();
    }
}
