package co.nyzo.verifier;

import co.nyzo.verifier.util.IpUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class Node implements MessageObject {

    private byte[] identifier;                    // wallet public key (32 bytes)
    private byte[] ipAddress;                     // IPv4 address, stored as bytes to keep memory predictable (4 bytes)
    private int portTcp;                          // TCP port number
    private int portUdp;                          // UDP port number, if available
    private long queueTimestamp;                  // this is the timestamp that determines queue placement -- it is
                                                  // when the verifier joined the mesh or when the verifier was last
                                                  // updated
    private long identifierChangeTimestamp;       // when the identifier at this IP was last changed
    private long inactiveTimestamp;               // when the verifier was marked as inactive; -1 for active verifiers

    public Node(byte[] identifier, byte[] ipAddress, int portTcp, int portUdp) {

        this.identifier = Arrays.copyOf(identifier, FieldByteSize.identifier);
        this.ipAddress = Arrays.copyOf(ipAddress, FieldByteSize.ipAddress);
        this.portTcp = portTcp;
        this.portUdp = portUdp;
        this.queueTimestamp = System.currentTimeMillis();
        this.identifierChangeTimestamp = System.currentTimeMillis();
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

    public int getPortTcp() {
        return portTcp;
    }

    public void setPortTcp(int portTcp) {
        this.portTcp = portTcp;
    }

    public int getPortUdp() {
        return portUdp;
    }

    public void setPortUdp(int portUdp) {
        this.portUdp = portUdp;
    }

    public long getQueueTimestamp() {
        return queueTimestamp;
    }

    public void setQueueTimestamp(long queueTimestamp) {
        this.queueTimestamp = queueTimestamp;
    }

    public long getIdentifierChangeTimestamp() {
        return identifierChangeTimestamp;
    }

    public void setIdentifierChangeTimestamp(long identifierChangeTimestamp) {
        this.identifierChangeTimestamp = identifierChangeTimestamp;
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
        buffer.putInt(portTcp);
        buffer.putLong(queueTimestamp);

        return result;
    }

    public static Node fromByteBuffer(ByteBuffer buffer) {

        byte[] identifier = new byte[FieldByteSize.identifier];
        buffer.get(identifier);
        byte[] ipAddress = new byte[FieldByteSize.ipAddress];
        buffer.get(ipAddress);
        int portTcp = buffer.getInt();
        long queueTimestamp = buffer.getLong();

        Node node = new Node(identifier, ipAddress, portTcp, -1);
        node.setQueueTimestamp(queueTimestamp);

        return node;
    }

    @Override
    public String toString() {
        return "[Node: " + IpUtil.addressAsString(getIpAddress()) + ":TCP=" + portTcp + ",UDP=" + portUdp + "]";
    }
}
