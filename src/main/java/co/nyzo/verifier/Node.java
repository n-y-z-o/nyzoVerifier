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
                                                  // when the verifier joined the mesh

    public Node(byte[] identifier, byte[] ipAddress, int port) {

        this.identifier = Arrays.copyOf(identifier, FieldByteSize.identifier);
        this.ipAddress = Arrays.copyOf(ipAddress, FieldByteSize.ipAddress);
        this.port = port;
        this.queueTimestamp = System.currentTimeMillis();
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

    // This method is used to set an initial queue timestamp when a node list is provided by another node or when
    // constructed from a byte buffer.
    public void setQueueTimestamp(long queueTimestamp) {
        this.queueTimestamp = queueTimestamp;
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
