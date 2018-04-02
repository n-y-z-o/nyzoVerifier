package co.nyzo.verifier;

import co.nyzo.verifier.util.IpUtil;

import java.util.*;

public class Node {

    private byte[] identifier;                    // wallet public key (32 bytes)
    private byte[] ipAddress;                     // IPv4 address, stored as bytes to keep memory predictable (4 bytes)
    private int port;                             // port number
    private long queueTimestamp;                  // this is the timestamp that determines queue placement -- it is
                                                  // the greater of the join timestamp and the timestamp of the last
                                                  // block that this verifier signed
    private boolean fullNode;

    public Node(byte[] identifier, byte[] ipAddress, int port, boolean fullNode) {

        this.identifier = Arrays.copyOf(identifier, FieldByteSize.identifier);
        this.ipAddress = Arrays.copyOf(ipAddress, FieldByteSize.ipAddress);
        this.port = port;
        this.queueTimestamp = System.currentTimeMillis();
        this.fullNode = fullNode;
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

    public void setIpAddress(byte[] ipAddress) {
        this.ipAddress = ipAddress;
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

    public boolean isFullNode() {
        return fullNode;
    }

    public void setFullNode(boolean fullNode) {
        this.fullNode = fullNode;
    }

    // This method is used to set an initial queue timestamp when a node list is provided by another node
    public void setQueueTimestamp(long queueTimestamp) {
        this.queueTimestamp = queueTimestamp;
    }

    // This method is used to send a node to the back of the queue after verifying a block.
    public void resetQueueTimestamp(long blockTimestamp) {
        this.queueTimestamp = blockTimestamp;
    }

    @Override
    public String toString() {
        return "[Node: " + IpUtil.addressAsString(getIpAddress()) + ":" + port + "]";
    }
}
