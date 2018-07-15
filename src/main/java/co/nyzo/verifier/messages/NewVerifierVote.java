package co.nyzo.verifier.messages;

import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.HashUtil;
import co.nyzo.verifier.MessageObject;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class NewVerifierVote implements MessageObject {

    private byte[] identifier;

    public NewVerifierVote(byte[] identifier) {

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

        return Arrays.copyOf(identifier, FieldByteSize.identifier);
    }

    public static NewVerifierVote fromByteBuffer(ByteBuffer buffer) {

        NewVerifierVote result = null;

        try {
            byte[] identifier = new byte[FieldByteSize.identifier];
            buffer.get(identifier);

            result = new NewVerifierVote(identifier);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    @Override
    public String toString() {
        return "[NewVerifierVote(identifier=" + PrintUtil.superCompactPrintByteArray(identifier) + ")]";
    }
}
