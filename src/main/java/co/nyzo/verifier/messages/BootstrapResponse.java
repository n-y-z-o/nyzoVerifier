package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class BootstrapResponse implements MessageObject {

    private long firstHashHeight;
    private List<byte[]> frozenBlockHashes;
    private List<Long> startHeights;
    private List<Transaction> transactionPool;
    private List<Block> unfrozenBlockPool;

    public BootstrapResponse() {

        List<byte[]> frozenBlockHashes = new ArrayList<>();
        List<Long> startHeights = new ArrayList<>();
        long height = BlockManager.getFrozenEdgeHeight();
        Block block = BlockManager.frozenBlockForHeight(height);
        long firstHashHeight = -1;
        while (frozenBlockHashes.size() < 5 && block != null && block.getCycleInformation() != null) {
            frozenBlockHashes.add(0, block.getHash());
            startHeights.add(0, block.getCycleInformation().getDeterminationHeight());
            firstHashHeight = block.getBlockHeight();
            height--;
            block = BlockManager.frozenBlockForHeight(height);
        }
        this.firstHashHeight = firstHashHeight;
        this.frozenBlockHashes = frozenBlockHashes;
        this.startHeights = startHeights;

        this.transactionPool = TransactionPool.allTransactions();
        this.unfrozenBlockPool = UnfrozenBlockManager.allUnfrozenBlocks();
    }

    public BootstrapResponse(long firstHashHeight, List<byte[]> frozenBlockHashes,
                             List<Long> startHeights, List<Transaction> transactionPool,
                             List<Block> unfrozenBlockPool) {

        this.firstHashHeight = firstHashHeight;
        this.frozenBlockHashes = frozenBlockHashes;
        this.startHeights = startHeights;
        this.transactionPool = transactionPool;
        this.unfrozenBlockPool = unfrozenBlockPool;
    }

    public long getFirstHashHeight() {
        return firstHashHeight;
    }

    public List<byte[]> getFrozenBlockHashes() {
        return frozenBlockHashes;
    }

    public List<Long> getStartHeights() {
        return startHeights;
    }

    public List<Transaction> getTransactionPool() {
        return transactionPool;
    }

    public List<Block> getUnfrozenBlockPool() {
        return unfrozenBlockPool;
    }

    @Override
    public int getByteSize() {

        // first hash height
        int byteSize = FieldByteSize.blockHeight;

        // frozen block hashes and start heights
        byteSize += FieldByteSize.hashListLength + frozenBlockHashes.size() * (FieldByteSize.hash +
                FieldByteSize.blockHeight);

        // transaction pool
        byteSize += FieldByteSize.transactionPoolLength;
        for (Transaction transaction : transactionPool) {
            byteSize += transaction.getByteSize();
        }

        // unfrozen block pool
        byteSize += FieldByteSize.unfrozenBlockPoolLength;
        for (Block block : unfrozenBlockPool) {
            byteSize += block.getByteSize();
        }

        return byteSize;
    }

    @Override
    public byte[] getBytes() {

        int size = getByteSize();
        byte[] result = new byte[size];
        ByteBuffer buffer = ByteBuffer.wrap(result);

        // first hash height
        buffer.putLong(firstHashHeight);

        // frozen block hashes and cycle lengths
        int numberOfBlocks = frozenBlockHashes.size();
        buffer.put((byte) numberOfBlocks);
        for (int i = 0; i < numberOfBlocks; i++) {
            buffer.put(frozenBlockHashes.get(i));
            buffer.putLong(startHeights.get(i));
        }

        // transaction pool
        buffer.putInt(transactionPool.size());
        for (Transaction transaction : transactionPool) {
            buffer.put(transaction.getBytes());
        }

        // unfrozen block pool
        buffer.putShort((short) unfrozenBlockPool.size());
        for (Block block : unfrozenBlockPool) {
            buffer.put(block.getBytes());
        }

        return result;
    }

    public static BootstrapResponse fromByteBuffer(ByteBuffer buffer) {

        BootstrapResponse result = null;

        try {
            // first hash height
            long firstHashHeight = buffer.getLong();

            // frozen block hashes and discontinuity determination heights
            byte numberOfHashes = buffer.get();
            List<byte[]> frozenBlockHashes = new ArrayList<>();
            List<Long> startHeights = new ArrayList<>();
            for (int i = 0; i < numberOfHashes; i++) {
                byte[] hash = new byte[FieldByteSize.hash];
                buffer.get(hash);
                frozenBlockHashes.add(hash);
                startHeights.add(buffer.getLong());
            }

            // transaction pool
            int numberOfTransactions = buffer.getInt();
            List<Transaction> transactionPool = new ArrayList<>();
            for (int i = 0; i < numberOfTransactions; i++) {
                transactionPool.add(Transaction.fromByteBuffer(buffer));
            }

            // unfrozen block pool
            short numberOfBlocks = buffer.getShort();
            List<Block> unfrozenBlockPool = new ArrayList<>();
            for (int i = 0; i < numberOfBlocks; i++) {
                unfrozenBlockPool.add(Block.fromByteBuffer(buffer));
            }

            result = new BootstrapResponse(firstHashHeight, frozenBlockHashes, startHeights, transactionPool,
                    unfrozenBlockPool);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("[BootstrapResponse(hashes=" +
                frozenBlockHashes.size() + ",transactions=" + transactionPool.size() + ",blocks=" +
                unfrozenBlockPool.size() + "]");

        return result.toString();
    }
}
