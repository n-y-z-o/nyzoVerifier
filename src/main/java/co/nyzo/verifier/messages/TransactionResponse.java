package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class TransactionResponse implements MessageObject {

    private boolean transactionAccepted;
    private String message;

    public TransactionResponse(Transaction transaction) {

        StringBuilder validationError = new StringBuilder();
        StringBuilder validationWarning = new StringBuilder();
        boolean transactionValid = transaction != null && transaction.performInitialValidation(validationError,
                validationWarning);

        if (transactionValid) {
            TransactionPool.addTransaction(transaction);
            transactionAccepted = true;
            message = "Your transaction from wallet " +
                    PrintUtil.compactPrintByteArray(transaction.getSenderIdentifier()) + " to " +
                    PrintUtil.compactPrintByteArray(transaction.getReceiverIdentifier()) +
                    " in the amount of " + PrintUtil.printAmount(transaction.getAmount()) +
                    " has been accepted by the system and is scheduled for incorporation into block " +
                    BlockManager.heightForTimestamp(transaction.getTimestamp()) + ".";
        } else {
            transactionAccepted = false;
            String errorString = "";
            if (validationError.length() > 0) {
                errorString = " (error=\"" + validationError.toString() + "\")";
            }
            message = "There was a problem and your transaction was not accepted by the system" + errorString +
                    ". To protect yourself against possible coin theft, please wait to resubmit this transaction. " +
                    "Refer to the Nyzo white paper for full details on why this is necessary, how long you need " +
                    "to wait, and to understand how Nyzo provides stronger protection than other blockchains " +
                    "against this type of potential vulnerability.";
        }
    }

    public boolean isTransactionAccepted() {
        return transactionAccepted;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(transactionAccepted ? (byte) 1 : (byte) 0);
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        buffer.putShort((short) messageBytes.length);
        buffer.put(messageBytes);

        return array;
    }

    @Override
    public int getByteSize() {
        return FieldByteSize.booleanField +   // transactionAccepted
            FieldByteSize.string(message);    // message
    }
}
