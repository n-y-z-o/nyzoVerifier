package co.nyzo.verifier.messages;

import co.nyzo.verifier.Message;
import co.nyzo.verifier.MessageObject;

import java.nio.ByteBuffer;

public class IpAddressMessageObject implements MessageObject {

    private byte[] ipAddress;

    public IpAddressMessageObject(byte[] ipAddress) {
        this.ipAddress = ipAddress == null || ipAddress.length != 4 ? new byte[4] : ipAddress;
    }

    public byte[] getIpAddress() {
        return ipAddress;
    }

    @Override
    public int getByteSize() {
        return 4;
    }

    @Override
    public byte[] getBytes() {
        return ipAddress;
    }

    public static IpAddressMessageObject fromByteBuffer(ByteBuffer buffer) {

        IpAddressMessageObject result = null;
        try {
            byte[] ipAddress = Message.getByteArray(buffer, 4);
            result = new IpAddressMessageObject(ipAddress);
        } catch (Exception ignored) { }

        return result;
    }
}
