package co.nyzo.verifier;

import java.nio.charset.StandardCharsets;

public class FieldByteSize {

    public static final int balanceListLength = 4;
    public static final int blockHeight = 8;
    public static final int blocksUntilFee = 2;
    public static final int booleanField = 1;
    public static final int cycleLength = 4;
    public static final int frozenBlockListLength = 2;
    public static final int hash = 32;
    public static final int hashListLength = 1;
    public static final int ipAddress = 4;
    public static final int maximumSenderDataLength = 32;
    public static final int messageLength = 4;
    public static final int port = 4;
    public static final int rolloverTransactionFees = 1;
    public static final int seed = 32;
    public static final int timestamp = 8;
    public static final int transactionAmount = 8;
    public static final int transactionType = 1;
    public static final int messageType = 2;
    public static final int identifier = 32;
    public static final int nodeListLength = 4;
    public static final int signature = 64;
    public static final int stringLength = 2;
    public static final int transactionPoolLength = 4;
    public static final int unfrozenBlockPoolLength = 2;
    public static final int unnamedByte = 1;
    public static final int unnamedDouble = 8;
    public static final int unnamedInteger = 4;
    public static final int unnamedShort = 2;
    public static final int voteListLength = 1;

    public static int string(String value) {
        return stringLength + (value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length);
    }

    public static int string(String value, int maximumStringByteLength) {
        return stringLength + (value == null ? 0 : Math.min(value.getBytes(StandardCharsets.UTF_8).length,
                maximumStringByteLength));
    }
}
