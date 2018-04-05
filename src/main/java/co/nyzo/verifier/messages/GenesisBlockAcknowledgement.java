package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class GenesisBlockAcknowledgement implements MessageObject {

    private boolean blockAccepted;
    private String message;

    public GenesisBlockAcknowledgement(Block block) {

        StringBuilder error = new StringBuilder();
        if (Block.isValidGenesisBlock(block, error)) {
            // TODO: initiate reset on this genesis block here
            blockAccepted = true;
            message = "Genesis block accepted";
        } else {
            blockAccepted = false;
            message = "Genesis block not accepted (error=" + error;
        }
    }

    public boolean isBlockAccepted() {
        return blockAccepted;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public int getByteSize() {
        return FieldByteSize.booleanField +   // transactionAccepted
                FieldByteSize.string(message);    // message
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(blockAccepted ? (byte) 1 : (byte) 0);
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        buffer.putShort((short) messageBytes.length);
        buffer.put(messageBytes);

        return array;
    }
}
