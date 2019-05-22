package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.IpUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BlockResponse implements MessageObject {

    private static int numberOfAdditionsToMapSinceCleaning = 0;
    private static final long minimumBalanceListRequestInterval = 1000L * 60L * 10L;  // 10 minutes
    private static final Map<ByteBuffer, Long> balanceListRequestIpToTimestampMap = new ConcurrentHashMap<>();

    private BalanceList initialBalanceList;
    private List<Block> blocks;

    public BlockResponse(long startBlockHeight, long endBlockHeight, boolean includeInitialBalanceList,
                         byte[] requestSourceIpAddress) {

        BalanceList initialBalanceList = null;
        List<Block> blocks = new ArrayList<>();

        // If the request asks for an initial balance list, the IP is not whitelisted, and the same source IP has
        // recently requested a balance list, provide an empty response.
        boolean requestIsValid = true;
        if (!Message.ipIsWhitelisted(requestSourceIpAddress) && includeInitialBalanceList) {
            ByteBuffer ipAddressBuffer = ByteBuffer.wrap(requestSourceIpAddress);
            long previousRequestTimestamp = balanceListRequestIpToTimestampMap.getOrDefault(ipAddressBuffer, 0L);
            if (previousRequestTimestamp > System.currentTimeMillis() - minimumBalanceListRequestInterval) {
                requestIsValid = false;
                byte[] identifier = NodeManager.identifierForIpAddress(requestSourceIpAddress);
                System.out.println("refusing to produce BlockResponse for " +
                        IpUtil.addressAsString(requestSourceIpAddress) + " (" + NicknameManager.get(identifier) + ")");
            } else {
                balanceListRequestIpToTimestampMap.put(ipAddressBuffer, System.currentTimeMillis());
            }

            if (numberOfAdditionsToMapSinceCleaning++ > 100) {
                numberOfAdditionsToMapSinceCleaning = 0;
                for (ByteBuffer ipAddress : new HashSet<>(balanceListRequestIpToTimestampMap.keySet())) {
                    if (balanceListRequestIpToTimestampMap.getOrDefault(ipAddress, 0L) < System.currentTimeMillis() -
                            minimumBalanceListRequestInterval) {
                        balanceListRequestIpToTimestampMap.remove(ipAddress);
                    }
                }
                System.out.println("cleaned BlockResponse timestamp map; size is now " +
                        balanceListRequestIpToTimestampMap.size());
            }
        }

        // To conserve resources, only respond to block requests for 10 or fewer blocks.
        if (requestIsValid && endBlockHeight - startBlockHeight < 10) {
            int totalByteSize = 0;
            boolean foundNullBlock = false;
            long blockHeight = endBlockHeight;
            while (totalByteSize < 1000000 && !foundNullBlock && blockHeight >= startBlockHeight) {
                Block block = BlockManager.frozenBlockForHeight(blockHeight);
                if (block == null) {
                    foundNullBlock = true;
                } else {
                    blocks.add(0, block);
                    totalByteSize += block.getByteSize();
                    if (blockHeight == startBlockHeight && includeInitialBalanceList) {
                        initialBalanceList = BalanceListManager.recentBalanceListForHeight(block.getBlockHeight());
                    }
                }

                blockHeight--;
            }
        }

        System.out.println("built list of " + blocks.size() + " for block request [" + startBlockHeight + "-" +
                endBlockHeight + "] with balance list " + initialBalanceList);

        this.initialBalanceList = initialBalanceList;
        this.blocks = blocks;
    }

    public BlockResponse(BalanceList initialBalanceList, List<Block> blocks) {

        this.initialBalanceList = initialBalanceList;
        this.blocks = blocks;
    }

    public BalanceList getInitialBalanceList() {
        return initialBalanceList;
    }

    public List<Block> getBlocks() {
        return blocks;
    }

    @Override
    public int getByteSize() {

        int byteSize = FieldByteSize.booleanField;  // boolean value indicating whether a balance list is included
        if (initialBalanceList != null) {
            byteSize += initialBalanceList.getByteSize();
        }

        byteSize += FieldByteSize.frozenBlockListLength;
        for (Block block : blocks) {
            byteSize += block.getByteSize();
        }

        return byteSize;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(initialBalanceList == null ? (byte) 0 : (byte) 1);
        if (initialBalanceList != null) {
            buffer.put(initialBalanceList.getBytes());
        }

        buffer.putShort((short) blocks.size());
        for (Block block : blocks) {
            buffer.put(block.getBytes());
        }

        return array;
    }

    public static BlockResponse fromByteBuffer(ByteBuffer buffer) {

        BlockResponse result = null;

        try {
            BalanceList initialBalanceList = null;
            if (buffer.get() == 1) {
                initialBalanceList = BalanceList.fromByteBuffer(buffer);
            }

            List<Block> blocks = new ArrayList<>();
            int numberOfBlocks = Math.min(buffer.getShort() & 0xffff, 10);
            for (int i = 0; i < numberOfBlocks; i++) {
                Block block = Block.fromByteBuffer(buffer);
                blocks.add(block);
                UnfrozenBlockManager.registerBlock(block);
            }

            result = new BlockResponse(initialBalanceList, blocks);
        } catch (Exception ignored) { }

        return result;
    }

    @Override
    public String toString() {
        return "[BlockResponse(blocks=" + blocks.size() + ")]";
    }
}
