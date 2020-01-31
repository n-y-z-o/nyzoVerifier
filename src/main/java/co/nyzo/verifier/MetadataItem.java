package co.nyzo.verifier;

import co.nyzo.verifier.util.PrintUtil;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class MetadataItem {

    private long timestamp;
    private String key;
    private byte[] senderIdentifier;
    private byte[] senderDataValue;
    private byte[] receiverIdentifier;

    public MetadataItem(long timestamp, String key, byte[] senderIdentifier,
                        byte[] senderDataValue, byte[] receiverIdentifier) {
        this.timestamp = timestamp;
        this.key = key;
        this.senderIdentifier = senderIdentifier;
        this.senderDataValue = senderDataValue == null ? new byte[0] : senderDataValue;
        this.receiverIdentifier = receiverIdentifier;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getKey() {
        return key;
    }

    public byte[] getSenderIdentifier() {
        return senderIdentifier;
    }

    public byte[] getSenderDataValue() {
        return senderDataValue;
    }

    public void setReceiverIdentifier(byte[] receiverIdentifier) {
        this.receiverIdentifier = receiverIdentifier;
    }

    public static MetadataItem fromTransaction(Transaction transaction) {

        MetadataItem result = null;

        // A metadata transaction must be a standard transaction, it must be 1 micronyzo, and its sender data must begin
        // with "meta-key", where "key" is the metadata key. If the sender data field is used for additional
        // information, the meta-key prefix must be separated from that information by a colon followed by a space
        // (": "). This is the same general form as NTTP sender data. The sender data must be greater than 5 characters
        // to allow at least one character for the metadata key.
        byte[] senderData = transaction.getSenderData();
        if (transaction.getAmount() == 1L && transaction.getType() == Transaction.typeStandard &&
                senderData.length > 5 && senderData[0] == 'm' && senderData[1] == 'e' && senderData[2] == 't' &&
                senderData[3] == 'a' && senderData[4] == '-') {

            // Find the separator index. Start looking at index 6 to ensure the key is non-empty.
            int separatorIndex = senderData.length;
            for (int i = 6; i < senderData.length - 1 && separatorIndex == senderData.length; i++) {
                if (senderData[i] == ':' && senderData[i + 1] == ' ') {
                    separatorIndex = i;
                }
            }

            // Get the key first. The key must be ASCII alphanumeric or dashes [a-z, A-Z, 0-9, '-'].
            byte[] keyBytes = Arrays.copyOfRange(senderData, 5, separatorIndex);
            boolean keyIsValid = true;
            for (int i = 0; i < keyBytes.length && keyIsValid; i++) {
                keyIsValid = (keyBytes[i] >= 'a' && keyBytes[i] <= 'z') ||   // 0x61 to 0x7A
                        (keyBytes[i] >= 'A' && keyBytes[i] <= 'Z') ||        // 0x41 to 0x5A
                        (keyBytes[i] >= '0' && keyBytes[i] <= '9') ||        // 0x30 to 0x39
                        keyBytes[i] == '-';                                  // 0x2D
            }

            // If the key is valid, assemble the metadata item.
            if (keyIsValid) {
                String key = new String(keyBytes, StandardCharsets.UTF_8);
                byte[] senderDataValue = Arrays.copyOfRange(senderData, Math.min(senderData.length, separatorIndex + 2),
                        senderData.length);

                result = new MetadataItem(transaction.getTimestamp(), key, transaction.getSenderIdentifier(),
                        senderDataValue, transaction.getReceiverIdentifier());
            }
        }

        return result;
    }

    public Transaction generateTransaction(Block previousBlock) {

        // This method is only valid for transactions coming from the local verifier.
        Transaction transaction = null;
        if (ByteUtil.arraysAreEqual(senderIdentifier, Verifier.getIdentifier())) {

            // Assemble the sender-data array.
            String tag = "meta-" + key + (senderDataValue.length == 0 ? "" : ": ");
            byte[] tagBytes = tag.getBytes(StandardCharsets.UTF_8);
            byte[] combinedBytes = new byte[tagBytes.length + senderDataValue.length];
            System.arraycopy(tagBytes, 0, combinedBytes, 0, tagBytes.length);
            System.arraycopy(senderDataValue, 0, combinedBytes, tagBytes.length, senderDataValue.length);
            if (combinedBytes.length > FieldByteSize.maximumSenderDataLength) {
                combinedBytes = Arrays.copyOf(combinedBytes, FieldByteSize.maximumSenderDataLength);
            }

            byte[] receiverIdentifierForTransaction = receiverIdentifier == null ? new byte[FieldByteSize.identifier] :
                    Arrays.copyOf(receiverIdentifier, FieldByteSize.identifier);

            transaction = Transaction.standardTransaction(timestamp, 1L, receiverIdentifierForTransaction,
                    previousBlock.getBlockHeight(), previousBlock.getHash(), combinedBytes, Verifier.getPrivateSeed());
        }

        return transaction;
    }

    @Override
    public String toString() {
        return "[MetadataItem: timestamp=" + timestamp + ", key=" + key + ", sender=" +
                PrintUtil.compactPrintByteArray(senderIdentifier) + ", sender data=" +
                PrintUtil.compactPrintByteArray(senderDataValue) + ", receiver ID=" +
                PrintUtil.compactPrintByteArray(receiverIdentifier) + "]";
    }
}
