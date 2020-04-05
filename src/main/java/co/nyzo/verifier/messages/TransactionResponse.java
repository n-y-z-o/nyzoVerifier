package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class TransactionResponse implements MessageObject {

    private boolean accepted;
    private String message;

    public TransactionResponse(Transaction transaction) {

        StringBuilder error = new StringBuilder();
        StringBuilder warning = new StringBuilder();
        boolean transactionValid = transaction != null && transaction.performInitialValidation(error,
                warning);

        accepted = false;
        if (transactionValid) {
            boolean addedToPool = TransactionPool.addTransaction(transaction, error, warning);
            if (addedToPool) {
                String warningString = "";
                if (warning.length() > 0) {
                    warningString = " (warning=\"" + warning.toString().trim() + "\")";
                }

                long height = BlockManager.heightForTimestamp(transaction.getTimestamp());
                accepted = true;
                message = "Your transaction from wallet " +
                        PrintUtil.compactPrintByteArray(transaction.getSenderIdentifier()) + " to " +
                        PrintUtil.compactPrintByteArray(transaction.getReceiverIdentifier()) +
                        " in the amount of " + PrintUtil.printAmount(transaction.getAmount()) +
                        " has been accepted by the system and is scheduled for incorporation into block " +
                        height + "." + warningString;
            }
        }

        if (!accepted) {
            String errorString = "";
            if (error.length() > 0) {
                errorString = " (error=\"" + error.toString().trim() + "\")";
            }
            message = "There was a problem and your transaction was not accepted by the system" + errorString +
                    ". To protect yourself against possible coin theft, please wait to resubmit this transaction. " +
                    "Refer to the Nyzo white paper for full details on why this is necessary, how long you need " +
                    "to wait, and to understand how Nyzo provides stronger protection than other blockchains " +
                    "against this type of potential vulnerability.";
        }
    }

    private TransactionResponse(boolean accepted, String message) {

        this.accepted = accepted;
        this.message = message;
    }

    public boolean isAccepted() {
        return accepted;
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
        buffer.put(accepted ? (byte) 1 : (byte) 0);
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        buffer.putShort((short) messageBytes.length);
        buffer.put(messageBytes);

        return array;
    }

    public static TransactionResponse fromByteBuffer(ByteBuffer buffer) {

        boolean transactionAccepted = buffer.get() == 1;
        short messageLength = buffer.getShort();
        byte[] messageBytes = new byte[messageLength];
        buffer.get(messageBytes);
        String message = new String(messageBytes, StandardCharsets.UTF_8);

        return new TransactionResponse(transactionAccepted, message);
    }

    @Override
    public String toString() {
        return "[TransactionResponse(accepted=" + accepted + ", message=" + message + "]";
    }
}
