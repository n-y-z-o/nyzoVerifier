package co.nyzo.verifier.messages;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.HashUtil;
import co.nyzo.verifier.MessageObject;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class NewVerifierVote implements MessageObject {

    private byte[] identifier;
    private byte[] ipAddress;
    private ByteBuffer byteBuffer;  // for the hashCode and equals methods

    public NewVerifierVote(byte[] identifier, byte[] ipAddress) {

        this.identifier = identifier;
        this.ipAddress = ipAddress;

        byte[] bufferArray = new byte[FieldByteSize.identifier + FieldByteSize.ipAddress];
        byteBuffer = ByteBuffer.wrap(bufferArray);
        byteBuffer.put(this.identifier);
        byteBuffer.put(this.byteBuffer);
        byteBuffer.rewind();  // hashCode is calculated from position
    }

    public byte[] getIdentifier() {
        return identifier;
    }

    public byte[] getIpAddress() {
        return ipAddress;
    }

    @Override
    public int getByteSize() {
        return byteBuffer.array().length;
    }

    @Override
    public byte[] getBytes() {

        return Arrays.copyOf(byteBuffer.array(), byteBuffer.array().length);
    }

    public static NewVerifierVote fromByteBuffer(ByteBuffer buffer) {

        NewVerifierVote result = null;

        try {
            byte[] identifier = new byte[FieldByteSize.identifier];
            buffer.get(identifier);
            byte[] ipAddress = new byte[FieldByteSize.ipAddress];
            buffer.get(ipAddress);

            result = new NewVerifierVote(identifier, ipAddress);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof NewVerifierVote) && ((NewVerifierVote) obj).byteBuffer.equals(byteBuffer);
    }

    @Override
    public int hashCode() {
        return byteBuffer.hashCode();
    }
}
