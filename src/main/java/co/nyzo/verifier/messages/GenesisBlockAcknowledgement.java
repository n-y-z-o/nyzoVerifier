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
            message = "Genesis block not accepted (error=\"" + error + "\")";
        }
    }

    private GenesisBlockAcknowledgement(boolean blockAccepted, String message) {
        this.blockAccepted = blockAccepted;
        this.message = message;
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

    public static GenesisBlockAcknowledgement fromByteBuffer(ByteBuffer buffer) {

        GenesisBlockAcknowledgement result = null;

        try {
            boolean blockAccepted = buffer.get() == 1;
            short messageByteLength = buffer.getShort();
            byte[] messageBytes = new byte[messageByteLength];
            buffer.get(messageBytes);
            String message = new String(messageBytes, StandardCharsets.UTF_8);

            result = new GenesisBlockAcknowledgement(blockAccepted, message);

        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }
}
