package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.IpUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BalanceListResponse implements MessageObject {

    private static int numberOfAdditionsToMapSinceCleaning = 0;
    private static final long minimumRequestInterval = 1000L * 60L * 10L;  // 10 minutes
    private static final Map<ByteBuffer, Long> requestIpToTimestampMap = new ConcurrentHashMap<>();

    private BalanceList balanceList;

    public BalanceListResponse(byte[] requestSourceIpAddress) {

        BalanceList initialBalanceList = null;
        List<Block> blocks = new ArrayList<>();

        // If the same source IP is not whitelisted and has recently requested a balance list, provide an empty
        // response.
        boolean requestIsValid = true;
        if (!Message.ipIsWhitelisted(requestSourceIpAddress)) {
            ByteBuffer ipAddressBuffer = ByteBuffer.wrap(requestSourceIpAddress);
            long previousRequestTimestamp = requestIpToTimestampMap.getOrDefault(ipAddressBuffer, 0L);
            if (previousRequestTimestamp > System.currentTimeMillis() - minimumRequestInterval) {
                requestIsValid = false;
                byte[] identifier = NodeManager.identifierForIpAddress(requestSourceIpAddress);
                System.out.println("refusing to produce BalanceListResponse for " +
                        IpUtil.addressAsString(requestSourceIpAddress) + " (" + NicknameManager.get(identifier) + ")");
            } else {
                requestIpToTimestampMap.put(ipAddressBuffer, System.currentTimeMillis());
            }
        }

        if (numberOfAdditionsToMapSinceCleaning++ > 100) {
            numberOfAdditionsToMapSinceCleaning = 0;
            for (ByteBuffer ipAddress : new HashSet<>(requestIpToTimestampMap.keySet())) {
                if (requestIpToTimestampMap.getOrDefault(ipAddress, 0L) < System.currentTimeMillis() -
                        minimumRequestInterval) {
                    requestIpToTimestampMap.remove(ipAddress);
                }
            }
            System.out.println("cleaned BalanceListResponse timestamp map; size is now " +
                    requestIpToTimestampMap.size());
        }

        this.balanceList = requestIsValid ? BalanceListManager.getFrozenEdgeList() : null;
    }

    private BalanceListResponse(BalanceList balanceList) {

        this.balanceList = balanceList;
    }

    public BalanceList getBalanceList() {
        return balanceList;
    }

    @Override
    public int getByteSize() {

        int byteSize = FieldByteSize.booleanField;  // boolean value indicating whether a balance list is included
        if (balanceList != null) {
            byteSize += balanceList.getByteSize();
        }

        return byteSize;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(balanceList == null ? (byte) 0 : (byte) 1);
        if (balanceList != null) {
            buffer.put(balanceList.getBytes());
        }

        return array;
    }

    public static BalanceListResponse fromByteBuffer(ByteBuffer buffer) {

        BalanceListResponse result = null;

        try {
            BalanceList balanceList = null;
            if (buffer.get() == 1) {
                balanceList = BalanceList.fromByteBuffer(buffer);
            }

            result = new BalanceListResponse(balanceList);
        } catch (Exception ignored) { }

        return result;
    }

    @Override
    public String toString() {
        return "[BalanceListResponse(balanceList=" + (balanceList == null ? "(null)" :
                balanceList.getBlockHeight()) + ")]";
    }
}
