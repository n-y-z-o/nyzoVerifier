package co.nyzo.verifier;

import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.SignatureUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class Transaction implements MessageObject {

    private enum SignatureState {
        Undetermined,
        Valid,
        Invalid
    }

    // We want this to be a functioning monetary system. The maximum number of coins is 100 million. The fraction used
    // for dividing coins is 1 million (all transactions must be a whole-number multiple of 1/1000000 coins).

    // If we have a coin value of $1 = ∩1, then the transaction increment is one-ten-thousandth of a cent, and the
    // market cap is $100 million. If we have a coin value of $100,000, then the transaction increment is $0.10,
    // and the market cap is $10 trillion.

    public static final long nyzosInSystem = 100000000L;
    public static final long micronyzoMultiplierRatio = 1000000L;
    public static final long micronyzosInSystem = nyzosInSystem * micronyzoMultiplierRatio;

    public static final byte typeCoinGeneration = 0;
    public static final byte typeSeed = 1;
    public static final byte typeStandard = 2;

    // Included in all transactions.
    private byte type;                   // 1 byte; 0=coin generation, 1=sender verification
    private long timestamp;              // 8 bytes; 64-bit Unix timestamp of the transaction initiation, in milliseconds
    private long amount;                 // 8 bytes; 64-bit amount in micronyzos
    private byte[] receiverIdentifier;   // 32 bytes (256-bit public key of the recipient)

    // Only included in type-1 and type-2 transactions
    private long previousHashHeight;     // 8 bytes; 64-bit index of the block height of the previous-block hash
    private byte[] previousBlockHash;    // 32 bytes (SHA-256 of a recent block in the chain)
    private byte[] senderIdentifier;     // 32 bytes (256-bit public key of the sender)
    private byte[] senderData;           // up to 32 bytes
    private byte[] signature;            // 64 bytes (512-bit signature)

    private SignatureState signatureState = SignatureState.Undetermined;

    private Transaction() {
    }

    public byte getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getAmount() {
        return amount;
    }

    public long getAmountAfterFee() {
        return amount - getFee();
    }

    public byte[] getReceiverIdentifier() {
        return receiverIdentifier;
    }

    public long getPreviousHashHeight() {
        if (previousBlockHash == null) {
            assignPreviousBlockHash();
        }

        return previousHashHeight;
    }

    public byte[] getPreviousBlockHash() {
        if (previousBlockHash == null) {
            assignPreviousBlockHash();
        }

        return previousBlockHash;
    }

    public byte[] getSenderIdentifier() {
        return senderIdentifier;
    }

    public byte[] getSenderData() {
        return senderData;
    }

    public byte[] getSignature() {
        return signature;
    }

    private void assignPreviousBlockHash() {

        previousHashHeight = BlockManager.getFrozenEdgeHeight();
        previousBlockHash = BlockManager.frozenBlockForHeight(previousHashHeight).getHash();
    }

    public static Transaction coinGenerationTransaction(long timestamp, long amount, byte[] receiverIdentifier) {

        Transaction transaction = new Transaction();
        transaction.type = typeCoinGeneration;
        transaction.timestamp = timestamp;
        transaction.amount = amount;
        transaction.receiverIdentifier = receiverIdentifier;

        return transaction;
    }

    public static Transaction seedTransaction(long timestamp, long amount, byte[] receiverIdentifier,
                                              long previousHashHeight, byte[] previousBlockHash,
                                              byte[] senderIdentifier, byte[] senderData, byte[] signature) {

        Transaction transaction = new Transaction();
        transaction.type = typeSeed;
        transaction.timestamp = timestamp;
        transaction.amount = amount;
        transaction.receiverIdentifier = Arrays.copyOf(receiverIdentifier, FieldByteSize.identifier);
        transaction.previousHashHeight = previousHashHeight;
        transaction.previousBlockHash = Arrays.copyOf(previousBlockHash, FieldByteSize.hash);
        transaction.senderIdentifier = Arrays.copyOf(senderIdentifier, FieldByteSize.identifier);
        transaction.senderData = Arrays.copyOf(senderData, Math.min(senderData.length, 32));
        transaction.signature = signature;

        return transaction;
    }

    public static Transaction seedTransaction(long timestamp, long amount, byte[] receiverIdentifier,
                                              long previousHashHeight, byte[] previousBlockHash,
                                              byte[] senderData, byte[] signerSeed) {

        Transaction transaction = new Transaction();
        transaction.type = typeSeed;
        transaction.timestamp = timestamp;
        transaction.amount = amount;
        transaction.receiverIdentifier = receiverIdentifier;
        transaction.previousHashHeight = previousHashHeight;
        transaction.previousBlockHash = previousBlockHash;
        transaction.senderIdentifier = KeyUtil.identifierForSeed(signerSeed);
        transaction.senderData = senderData;
        transaction.signature = SignatureUtil.signBytes(transaction.getBytes(true), signerSeed);

        return transaction;
    }

    public static Transaction standardTransaction(long timestamp, long amount, byte[] receiverIdentifier,
                                                  long previousHashHeight, byte[] previousBlockHash,
                                                  byte[] senderIdentifier, byte[] senderData, byte[] signature) {

        Transaction transaction = new Transaction();
        transaction.type = typeStandard;
        transaction.timestamp = timestamp;
        transaction.amount = amount;
        transaction.receiverIdentifier = Arrays.copyOf(receiverIdentifier, FieldByteSize.identifier);
        transaction.previousHashHeight = previousHashHeight;
        transaction.previousBlockHash = Arrays.copyOf(previousBlockHash, FieldByteSize.hash);
        transaction.senderIdentifier = Arrays.copyOf(senderIdentifier, FieldByteSize.identifier);
        transaction.senderData = Arrays.copyOf(senderData, Math.min(senderData.length, 32));
        transaction.signature = signature;

        return transaction;
    }

    public static Transaction standardTransaction(long timestamp, long amount, byte[] receiverIdentifier,
                                                  long previousHashHeight, byte[] previousBlockHash,
                                                  byte[] senderData, byte[] signerSeed) {

        Transaction transaction = new Transaction();
        transaction.type = typeStandard;
        transaction.timestamp = timestamp;
        transaction.amount = amount;
        transaction.receiverIdentifier = receiverIdentifier;
        transaction.previousHashHeight = previousHashHeight;
        transaction.previousBlockHash = previousBlockHash;
        transaction.senderIdentifier = KeyUtil.identifierForSeed(signerSeed);
        transaction.senderData = senderData;
        transaction.signature = SignatureUtil.signBytes(transaction.getBytes(true), signerSeed);

        return transaction;
    }

    public long getFee() {

        return (getAmount() + 399L) / 400L;
    }

    @Override
    public int getByteSize() {
        return getByteSize(false);
    }

    public int getByteSize(boolean forSigning) {

        int size = FieldByteSize.transactionType +    // type
                FieldByteSize.timestamp +             // timestamp
                FieldByteSize.transactionAmount +     // amount
                FieldByteSize.identifier;             // receiver identifier

        if (type == typeSeed || type == typeStandard) {

            if (forSigning) {
                size += FieldByteSize.hash;           // previous-block hash for signing
            } else {
                size += FieldByteSize.blockHeight;    // previous-hash height for storage and transmission
            }
            size += FieldByteSize.identifier;         // sender identifier

            if (forSigning) {
                size += FieldByteSize.hash;           // sender data hash for signing
            } else {
                size += 1 + senderData.length +       // length specifier + sender data
                        FieldByteSize.signature;      // transaction signature
            }
        }

        return size;
    }

    @Override
    public byte[] getBytes() {

        return getBytes(false);
    }

    public byte[] getBytes(boolean forSigning) {

        byte[] array = new byte[getByteSize(forSigning)];

        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.put(type);
        buffer.putLong(timestamp);
        buffer.putLong(amount);
        buffer.put(receiverIdentifier);

        if (type == typeSeed || type == typeStandard) {

            if (forSigning) {
                buffer.put(getPreviousBlockHash());      // may be null initially and need to be determined
            } else {
                buffer.putLong(getPreviousHashHeight()); // may be unspecified initially and need to be determined
            }
            buffer.put(senderIdentifier);

            // For serializing, we use the raw sender data with a length specifier. For signing, we use the double-
            // SHA-256 of the user data. This will allow us to remove inappropriate or illegal metadata from the
            // blockchain at a later date by replacing it with its double-SHA-256 without compromising the signature
            // integrity
            if (forSigning) {
                buffer.put(HashUtil.doubleSHA256(senderData));
            } else {
                buffer.put((byte) senderData.length);
                buffer.put(senderData);
            }

            if (!forSigning) {
                buffer.put(signature);
            }
        }

        return array;
    }

    public static Transaction fromByteBuffer(ByteBuffer buffer) {

        return fromByteBuffer(buffer, 0, new byte[FieldByteSize.hash]);
    }

    public static Transaction fromByteBuffer(ByteBuffer buffer, long transactionHeight, byte[] previousHashInChain) {

        // These are the fields contained in all transactions.
        byte type = buffer.get();
        long timestamp = buffer.getLong();
        long amount = buffer.getLong();
        byte[] recipientIdentifier = new byte[FieldByteSize.identifier];
        buffer.get(recipientIdentifier);

        // Build the transaction object, getting additional fields for type-1 and type-2 transactions.
        Transaction transaction = null;
        if (type == typeCoinGeneration) {
            transaction = coinGenerationTransaction(timestamp, amount, recipientIdentifier);
        } else if (type == typeSeed || type == typeStandard) {
            long previousHashHeight = buffer.getLong();
            Block previousHashBlock = previousHashBlockForHeight(previousHashHeight, transactionHeight,
                    previousHashInChain);
            byte[] previousBlockHash = previousHashBlock == null ? new byte[FieldByteSize.hash] :
                    previousHashBlock.getHash();
            byte[] senderIdentifier = new byte[FieldByteSize.identifier];
            buffer.get(senderIdentifier);

            int senderDataLength = Math.min(buffer.get(), 32);
            byte[] senderData = new byte[senderDataLength];
            buffer.get(senderData);

            byte[] signature = new byte[FieldByteSize.signature];
            buffer.get(signature);
            if (type == typeSeed) {
                transaction = seedTransaction(timestamp, amount, recipientIdentifier, previousHashHeight,
                        previousBlockHash, senderIdentifier, senderData, signature);
            } else {  // type == typeStandard
                transaction = standardTransaction(timestamp, amount, recipientIdentifier, previousHashHeight,
                        previousBlockHash, senderIdentifier, senderData, signature);
            }
        } else {
            System.err.println("Unknown type: " + type);
        }

        return transaction;
    }

    private static Block previousHashBlockForHeight(long hashHeight, long transactionHeight,
                                                    byte[] previousHashInChain) {

        // First, try to get a frozen block. If one is not available, and the height referenced is past the frozen edge,
        // try to get a block on the branch leading to this transaction.
        Block block = BlockManager.frozenBlockForHeight(hashHeight);
        if (block == null && hashHeight > BlockManager.getFrozenEdgeHeight()) {
            Block previousBlock = UnfrozenBlockManager.unverifiedBlockAtHeight(transactionHeight - 1,
                    previousHashInChain);
            while (previousBlock != null && previousBlock.getBlockHeight() > hashHeight) {
                previousBlock = UnfrozenBlockManager.unverifiedBlockAtHeight(previousBlock.getBlockHeight() - 1,
                        previousBlock.getPreviousBlockHash());
            }

            if (previousBlock != null && previousBlock.getBlockHeight() == hashHeight) {
                block = previousBlock;
            }
        }

        return block;
    }

    public boolean performInitialValidation(StringBuilder validationError, StringBuilder validationWarning) {

        // As its name indicates, this method performs initial validation of transactions so users know when a
        // transaction will not be added to the transaction pool. Passing of this validation only adds a transaction
        // to the pool and does not guarantee that the transaction will be incorporated into a black.

        // Additionally, to provide good feedback to users, we warn about transactions that will be added to the pool
        // but appear to have issues that may prevent their incorporation into blocks.

        boolean valid = true;

        try {

            // Check the type (we only validate transactions past block zero, so 1 and 2 are the only valid types
            // right now).
            if (type != typeSeed && type != typeStandard) {
                valid = false;
                validationError.append("Only seed (type-1) and standard (type-2) transactions are valid after block " +
                        "0. ");
            }

            // Check that the previous-block hash is contained in the chain.
            Block previousHashBlock = BlockManager.frozenBlockForHeight(previousHashHeight);
            if (valid && (previousHashBlock == null ||
                    !ByteUtil.arraysAreEqual(previousHashBlock.getHash(), previousBlockHash))) {
                valid = false;
                validationError.append("The previous-block hash is invalid. ");
            }


            // Check the signature.
            if (valid) {
                if (!signatureIsValid()) {
                    valid = false;
                    validationError.append("The signature is not valid. ");
                }
            }

            // Check that the amount is at least µ1.
            if (valid && amount < 1) {
                valid = false;
                validationError.append("The transaction must be at least µ1. ");
            }

            // Check that the sender and receiver are the same address for seed transactions and different addresses
            // for standard transactions.
            if (valid && type == typeSeed) {
                if (!ByteUtil.arraysAreEqual(getSenderIdentifier(), getReceiverIdentifier())) {
                    valid = false;
                    validationError.append("The sender and receiver must be the same for seed transactions. ");
                }
            } else if (valid && type == typeStandard) {
                if (ByteUtil.arraysAreEqual(getSenderIdentifier(), getReceiverIdentifier())) {
                    valid = false;
                    validationError.append("The sender and receiver must be different for standard transactions. ");
                }
            }

            // Check the height. If the block has already been frozen, reject the transaction. If the block is already
            // open for processing, produce a warning.
            if (valid) {
                long blockHeight = BlockManager.heightForTimestamp(timestamp);
                long openEdgeHeight = BlockManager.openEdgeHeight(false);
                if (blockHeight < openEdgeHeight) {
                    if (blockHeight <= BlockManager.getFrozenEdgeHeight()) {
                        valid = false;
                        validationError.append("This transaction's block has already been frozen. ");
                    } else {
                        validationWarning.append("This transaction's block is already open for processing, so this " +
                                "transaction may be received too late to be included. ");
                    }
                }
            }

            // Produce a warning for transactions that appear to be balance-list spam.
            if (valid) {
                BalanceList balanceList = BalanceListManager.getFrozenEdgeList();
                if (balanceList != null) {
                    Map<ByteBuffer, Long> balanceMap = BalanceManager.makeBalanceMap(balanceList);
                    if (BalanceManager.transactionSpamsBalanceList(balanceMap, this,
                            Collections.singletonList(this))) {

                        if (getAmount() < BalanceManager.minimumPreferredBalance) {
                            validationWarning.append("This transaction appears to create a new account with a ")
                                    .append("balance less than ")
                                    .append(PrintUtil.printAmount(BalanceManager.minimumPreferredBalance))
                                    .append(", so it may not be approved. ");
                        } else {
                            validationWarning.append("This transaction appears to leave a balance greater than ")
                                    .append("zero but less than ")
                                    .append(PrintUtil.printAmount(BalanceManager.minimumPreferredBalance))
                                    .append(" in the sender account, so it may not be approved. ");
                        }
                    }
                }
            }

            // Trim trailing spaces from the error and warning.
            if (validationError.length() > 0) {
                validationError.deleteCharAt(validationError.length() - 1);
            }
            if (validationWarning.length() > 0) {
                validationWarning.deleteCharAt(validationWarning.length() - 1);
            }

        } catch (Exception ignored) {
            valid = false;
            validationError.append("An unspecified validation error occurred. This typically indicates a malformed " +
                    "transaction. ");
        }

        return valid;
    }

    public boolean signatureIsValid() {

        if (signatureState == SignatureState.Undetermined) {
            signatureState = SignatureUtil.signatureIsValid(signature, getBytes(true), senderIdentifier) ?
                    SignatureState.Valid : SignatureState.Invalid;
        }

        return signatureState == SignatureState.Valid;
    }

    public boolean previousHashIsValid() {
        return true;
    }

}
