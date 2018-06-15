package co.nyzo.verifier.messages;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.HashUtil;

import java.nio.ByteBuffer;

public class NewVerifierVote {

    private byte[] identifier;
    private byte[] ipAddress;
    private ByteBuffer byteBuffer;

    public NewVerifierVote(byte[] identifier, byte[] ipAddress) {

        this.identifier = identifier;
        this.ipAddress = ipAddress;

        byte[] bufferArray = new byte[FieldByteSize.identifier + FieldByteSize.ipAddress];
        byteBuffer = ByteBuffer.wrap(bufferArray);
        byteBuffer.put(this.identifier);
        byteBuffer.put(this.byteBuffer);
        byteBuffer.rewind();
    }

    public byte[] getIdentifier() {
        return identifier;
    }

    public byte[] getIpAddress() {
        return ipAddress;
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
