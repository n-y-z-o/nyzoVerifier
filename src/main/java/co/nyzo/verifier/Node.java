package co.nyzo.verifier;

import co.nyzo.verifier.messages.TransactionPoolResponse;
import co.nyzo.verifier.util.IpUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class Node implements MessageObject {

    private byte[] identifier;                    // wallet public key (32 bytes)
    private byte[] ipAddress;                     // IPv4 address, stored as bytes to keep memory predictable (4 bytes)
    private int port;                             // port number
    private long queueTimestamp;                  // this is the timestamp that determines queue placement -- it is
                                                  // when the verifier joined the mesh or when the verifier was last
                                                  // updated
    private long inactiveTimestamp;               // when the verifier was marked as inactive; -1 for active verifiers

    public Node(byte[] identifier, byte[] ipAddress, int port) {

        this.identifier = Arrays.copyOf(identifier, FieldByteSize.identifier);
        this.ipAddress = Arrays.copyOf(ipAddress, FieldByteSize.ipAddress);
        this.port = port;
        this.queueTimestamp = System.currentTimeMillis();
        this.inactiveTimestamp = -1L;
    }

    public byte[] getIdentifier() {
        return identifier;
    }

    public void setIdentifier(byte[] identifier) {
        this.identifier = identifier;
    }

    public byte[] getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public long getQueueTimestamp() {
        return queueTimestamp;
    }

    public void setQueueTimestamp(long queueTimestamp) {
        this.queueTimestamp = queueTimestamp;
    }

    public long getInactiveTimestamp() {
        return inactiveTimestamp;
    }

    public void setInactiveTimestamp(long inactiveTimestamp) {
        this.inactiveTimestamp = inactiveTimestamp;
    }

     public boolean isActive() {
         return inactiveTimestamp < 0;
     }

    public static int getByteSizeStatic() {

        return FieldByteSize.identifier + FieldByteSize.ipAddress + FieldByteSize.port + FieldByteSize.timestamp;
    }

    @Override
    public int getByteSize() {

        return getByteSizeStatic();
    }

    @Override
    public byte[] getBytes() {

        byte[] result = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(result);
        buffer.put(identifier);
        buffer.put(ipAddress);
        buffer.putInt(port);
        buffer.putLong(queueTimestamp);

        return result;
    }

    public static Node fromByteBuffer(ByteBuffer buffer) {

        byte[] identifier = new byte[FieldByteSize.identifier];
        buffer.get(identifier);
        byte[] ipAddress = new byte[FieldByteSize.ipAddress];
        buffer.get(ipAddress);
        int port = buffer.getInt();
        long queueTimestamp = buffer.getLong();

        Node node = new Node(identifier, ipAddress, port);
        node.setQueueTimestamp(queueTimestamp);

        return node;
    }

    @Override
    public String toString() {
        return "[Node: " + IpUtil.addressAsString(getIpAddress()) + ":" + port + "]";
    }
}
