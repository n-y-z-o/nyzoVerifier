package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BootstrapResponseV2 implements MessageObject {

    private long frozenEdgeHeight;
    private byte[] frozenEdgeHash;
    private List<ByteBuffer> cycleVerifiers;

    public BootstrapResponseV2() {

        Block frozenEdge = BlockManager.getFrozenEdge();
        this.frozenEdgeHeight = frozenEdge == null ? -1 : frozenEdge.getBlockHeight();
        this.frozenEdgeHash = frozenEdge == null ? new byte[FieldByteSize.hash] : frozenEdge.getHash();
        this.cycleVerifiers = BlockManager.verifiersInCurrentCycleList();
    }

    public BootstrapResponseV2(long frozenEdgeHeight, byte[] frozenEdgeHash, List<ByteBuffer> cycleVerifiers) {

        this.frozenEdgeHeight = frozenEdgeHeight;
        this.frozenEdgeHash = frozenEdgeHash;
        this.cycleVerifiers = new ArrayList<>(cycleVerifiers);
    }

    public long getFrozenEdgeHeight() {
        return frozenEdgeHeight;
    }

    public byte[] getFrozenEdgeHash() {
        return frozenEdgeHash;
    }

    public List<ByteBuffer> getCycleVerifiers() {
        return cycleVerifiers;
    }

    @Override
    public int getByteSize() {

        return FieldByteSize.blockHeight + FieldByteSize.hash + FieldByteSize.unnamedShort +
                cycleVerifiers.size() * FieldByteSize.identifier;
    }

    @Override
    public byte[] getBytes() {

        int size = getByteSize();
        byte[] result = new byte[size];
        ByteBuffer buffer = ByteBuffer.wrap(result);

        // frozen-edge height and hash
        buffer.putLong(frozenEdgeHeight);
        buffer.put(frozenEdgeHash);

        // verifiers
        buffer.putShort((short) cycleVerifiers.size());
        for (ByteBuffer identifier : cycleVerifiers) {
            buffer.put(identifier.array());
        }

        return result;
    }

    public static BootstrapResponseV2 fromByteBuffer(ByteBuffer buffer) {

        BootstrapResponseV2 result = null;

        try {
            // frozen-edge height and hash
            long frozenEdgeHeight = buffer.getLong();
            byte[] frozenEdgeHash = Message.getByteArray(buffer, FieldByteSize.hash);

            // verifiers
            short numberOfVerifiers = buffer.getShort();
            List<ByteBuffer> cycleVerifiers = new ArrayList<>();
            for (int i = 0; i < numberOfVerifiers; i++) {
                cycleVerifiers.add(ByteBuffer.wrap(Message.getByteArray(buffer, FieldByteSize.identifier)));
            }

            result = new BootstrapResponseV2(frozenEdgeHeight, frozenEdgeHash, cycleVerifiers);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("[BootstrapResponseV2(frozenEdgeHeight=" +
                frozenEdgeHeight + ",frozenEdgeHash=" + PrintUtil.superCompactPrintByteArray(frozenEdgeHash) +
                ",cycleVerifiers=" + cycleVerifiers.size() + "]");

        return result.toString();
    }
}
