package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class NodeJoinResponse implements MessageObject {

    // TODO: change this class to provide the Genesis block, which is predictably small, and the heights and hashes of
    // TODO: the five highest frozen blocks

    private Block genesisBlock;
    private List<Long> blockHeights;
    private List<byte[]> blockHashes;

    public NodeJoinResponse() {

        genesisBlock = BlockManager.frozenBlockForHeight(0);

        long highestBlockFrozen = BlockManager.highestBlockFrozen();
        blockHeights = new ArrayList<>();
        blockHashes = new ArrayList<>();
        for (long i = Math.max(1L, highestBlockFrozen - 4L); i <= highestBlockFrozen; i++) {
            Block block = BlockManager.frozenBlockForHeight(i);
            if (block != null) {
                blockHeights.add(block.getBlockHeight());
                blockHashes.add(block.getHash());
            }
        }
    }

    private NodeJoinResponse(Block genesisBlock, List<Long> blockHeights, List<byte[]> blockHashes) {

        this.genesisBlock = genesisBlock;
        this.blockHeights = blockHeights;
        this.blockHashes = blockHashes;
    }

    public Block getGenesisBlock() {
        return genesisBlock;
    }

    public List<Long> getBlockHeights() {
        return new ArrayList<>(blockHeights);
    }

    public List<byte[]> getBlockHashes() {
        return blockHashes;
    }

    @Override
    public int getByteSize() {

        int size;
        if (genesisBlock == null) {
            size = FieldByteSize.blockHeight;
        } else {
            int numberOfBlocks = blockHeights.size();

            size = genesisBlock.getByteSize() +
                    Byte.BYTES +  // list size
                    numberOfBlocks * (FieldByteSize.blockHeight + FieldByteSize.hash);
        }

        return size;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        if (genesisBlock == null) {
            buffer.putLong(-1);
        } else {
            buffer.put(genesisBlock.getBytes());

            int numberOfBlocks = blockHeights.size();
            buffer.put((byte) numberOfBlocks);
            for (int i = 0; i < numberOfBlocks; i++) {
                buffer.putLong(blockHeights.get(i));
                buffer.put(blockHashes.get(i));
            }
        }

        return array;
    }

    public static NodeJoinResponse fromByteBuffer(ByteBuffer buffer) {

        NodeJoinResponse result = null;

        try {
            Block genesisBlock = buffer.remaining() < 100 ? null : Block.fromByteBuffer(buffer);
            List<Long> blockHeights = new ArrayList<>();
            List<byte[]> blockHashes = new ArrayList<>();
            if (genesisBlock != null) {
                int numberOfBlocks = buffer.get();
                for (int i = 0; i < numberOfBlocks; i++) {
                    blockHeights.add(buffer.getLong());
                    byte[] hash = new byte[FieldByteSize.hash];
                    buffer.get(hash);
                    blockHashes.add(hash);
                }
            }

            result = new NodeJoinResponse(genesisBlock, blockHeights, blockHashes);

        } catch (Exception ignored) { }

        return result;
    }
}
