package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class NewVerifierVoteOverrideRequest implements MessageObject {

    private final byte[] identifier;

    public NewVerifierVoteOverrideRequest(byte[] identifier) {

        this.identifier = identifier;
    }

    public byte[] getIdentifier() {
        return identifier;
    }

    @Override
    public int getByteSize() {

        return FieldByteSize.identifier;
    }

    @Override
    public byte[] getBytes() {

        return Arrays.copyOf(identifier, getByteSize());
    }

    public static NewVerifierVoteOverrideRequest fromByteBuffer(ByteBuffer buffer) {

        byte[] identifier = Message.getByteArray(buffer, FieldByteSize.identifier);

        return new NewVerifierVoteOverrideRequest(identifier);
    }

    @Override
    public String toString() {
        return "[NewVerifierVoteOverrideRequest(id=" + ByteUtil.arrayAsStringWithDashes(identifier) + "]";
    }
}
