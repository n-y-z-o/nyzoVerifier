package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.nio.ByteBuffer;

public class NewBlockMessage implements MessageObject {

    private Block block;
    private int port;

    public NewBlockMessage(Block block) {
        this.block = block;
        this.port = -1;
    }

    private NewBlockMessage(Block block, int port) {
        this.block = block;
        this.port = port;
    }

    public Block getBlock() {
        return block;
    }

    @Override
    public int getByteSize() {

        return block.getByteSize() + FieldByteSize.port;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(block.getBytes());
        buffer.putInt(port);  // The port is no longer used. It is included to ensure signature integrity.

        return array;
    }

    public static NewBlockMessage fromByteBuffer(ByteBuffer buffer, byte[] senderIdentifier) {

        NewBlockMessage result = null;
        try {
            // This verifier can be overwhelmed processing incoming blocks. To mitigate this, large blocks are only
            // accepted from verifiers that are expected to send large blocks. Peek at the number of transactions in the
            // block before deserialization. The number of transactions starts at byte index 56 in the buffer.
            int numberOfTransactions = buffer.getInt(buffer.position() + 56);
            long blockHeight = ShortLong.fromCombinedValue(buffer.getLong(buffer.position())).getLongValue();

            // If the block is under the frozen edge or pas the open edge, do not process it. If the height is good and
            // the number of transactions is 2 or fewer, process it. Otherwise, look more carefully. Only in-cycle
            // verifiers should ever produce large blocks.
            boolean shouldProcess;
            if (blockHeight <= BlockManager.getFrozenEdgeHeight() || blockHeight > BlockManager.openEdgeHeight(true)) {
                shouldProcess = false;
            } else if (numberOfTransactions <= 2) {
                shouldProcess = true;
            } else {
                if (BlockManager.verifierInCurrentCycle(ByteBuffer.wrap(senderIdentifier)) &&
                        numberOfTransactions < BlockchainMetricsManager.maximumTransactionsForBlockAssembly() + 10) {
                    shouldProcess = true;
                } else {
                    shouldProcess = false;
                }
            }

            if (shouldProcess) {
                Block block = Block.fromByteBuffer(buffer);
                int port = buffer.getInt();  // The port is no longer used. It is stored to ensure signature integrity.
                result = new NewBlockMessage(block, port);
            }
        } catch (Exception ignored) { }

        return result;
    }
}
