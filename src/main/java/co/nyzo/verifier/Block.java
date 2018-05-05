package co.nyzo.verifier;

import co.nyzo.verifier.util.DebugUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.SignatureUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class Block implements MessageObject {

    public enum DiscontinuityState {
        Undetermined,
        IsDiscontinuity,
        IsNotDiscontinuity
    }

    public static final byte[] genesisVerifier = ByteUtil.byteArrayFromHexString("6b32332d4b28e6ad-" +
            "d7b8f86f374045ca-fc6453344a1c47b6-feaf485f8c2e0d47", 32);

    public static final byte[] genesisBlockHash = HashUtil.doubleSHA256(new byte[0]);

    public static long genesisBlockStartTimestamp = -1L;
    public static final long blockDuration = 5000L;

    private long height;                           // 8 bytes; 64-bit integer block height from the Genesis block,
                                                   // which has a height of 0
    private byte[] previousBlockHash;              // 32 bytes (this is the double-SHA-256 of the previous block
                                                   // signature)
    private long startTimestamp;                   // 8 bytes; 64-bit Unix timestamp of the start of the block, in
                                                   // milliseconds
    private long verificationTimestamp;            // 8 bytes; 64-bit Unix timestamp of when the verifier creates the
                                                   // block, in milliseconds
    private List<Transaction> transactions;        // 4 bytes for number + variable
    private byte[] balanceListHash;                // 32 bytes (this is the double-SHA-256 of the account balance list)
    private BalanceList balanceList;               // stored separately - the hash is stored in the block
    private byte[] verifierIdentifier;             // 32 bytes
    private byte[] verifierSignature;              // 64 bytes
    private Block previousBlock;                   // TODO: unset this periodically on old blocks to control memory use
    private DiscontinuityState discontinuityState;
    private long discontinuityDeterminationHeight;


    private CycleInformation cycleInformation = null;

    public Block(long height, byte[] previousBlockHash, long startTimestamp, List<Transaction> transactions,
                 byte[] balanceListHash, BalanceList balanceList) {

        this.height = height;
        this.previousBlockHash = previousBlockHash;
        this.startTimestamp = startTimestamp;
        this.verificationTimestamp = System.currentTimeMillis();
        this.transactions = new ArrayList<>(transactions);
        this.balanceListHash = balanceListHash;
        this.balanceList = balanceList;
        this.previousBlock = null;
        this.discontinuityState = height == 0 ? DiscontinuityState.IsNotDiscontinuity : DiscontinuityState.Undetermined;
        this.discontinuityDeterminationHeight = height == 0 ? 0 : -1;

        try {
            this.verifierIdentifier = Verifier.getIdentifier();
            this.verifierSignature = Verifier.sign(getBytes(false));
        } catch (Exception e) {
            this.verifierIdentifier = new byte[32];
            this.verifierSignature = new byte[64];
        }
    }

    private Block(long height, byte[] previousBlockHash, long startTimestamp, long verificationTimestamp,
                  List<Transaction> transactions, byte[] balanceListHash, byte[] verifierIdentifier,
                  byte[] verifierSignature) {

        this.height = height;
        this.previousBlockHash = previousBlockHash;
        this.startTimestamp = startTimestamp;
        this.verificationTimestamp = verificationTimestamp;
        this.transactions = transactions;
        this.balanceListHash = balanceListHash;
        this.verifierIdentifier = verifierIdentifier;
        this.verifierSignature = verifierSignature;
        this.previousBlock = null;
        this.discontinuityState = height == 0 ? DiscontinuityState.IsNotDiscontinuity : DiscontinuityState.Undetermined;
        this.discontinuityDeterminationHeight = height == 0 ? 0 : -1;
    }

    public long getBlockHeight() {
        return height;
    }

    public byte[] getPreviousBlockHash() {
        return previousBlockHash;
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public long getVerificationTimestamp() {
        return verificationTimestamp;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public byte[] getHash() {
        return HashUtil.doubleSHA256(verifierSignature);
    }

    public byte[] getBalanceListHash() {
        return balanceListHash;
    }

    public BalanceList getBalanceList() {

        if (balanceList == null && previousBlock != null) {
            setBalanceList(balanceListForNextBlock(previousBlock, transactions, verifierIdentifier));
        }

        return balanceList;
    }

    public void setBalanceList(BalanceList balanceList) {

        if (balanceList != null) {
            if (ByteUtil.arraysAreEqual(HashUtil.doubleSHA256(balanceList.getBytes()), balanceListHash)) {
                this.balanceList = balanceList;
            } else {
                System.err.println("balance list does not match hash! (" + balanceList.getBlockHeight() + ") " +
                        DebugUtil.callingMethods(3));
            }
        }
    }

    public Block getPreviousBlock() {

        if (previousBlock == null && getBlockHeight() - 1 <= BlockManager.highestBlockFrozen()) {
            setPreviousBlock(BlockManager.frozenBlockForHeight(getBlockHeight() - 1));
        }

        return previousBlock;
    }

    public void setPreviousBlock(Block previousBlock) {

        if (previousBlock == null) {
            this.previousBlock = null;
        } else if (!ByteUtil.arraysAreEqual(previousBlock.getHash(), previousBlockHash)) {
            System.err.println("previous block DOES NOT match hash! previous block height=" +
                    previousBlock.getBlockHeight() + ", block height=" + this.getBlockHeight() +
                    ", previousBlockHash=" + ByteUtil.arrayAsStringWithDashes(previousBlockHash) +
                    ", previousBlock.hash=" + ByteUtil.arrayAsStringWithDashes(previousBlockHash));
        } else {
            this.previousBlock = previousBlock;
        }
    }

    public byte[] getVerifierIdentifier() {
        return verifierIdentifier;
    }

    public byte[] getVerifierSignature() {
        return verifierSignature;
    }

    public DiscontinuityState getDiscontinuityState() {

        if (discontinuityState == DiscontinuityState.Undetermined) {
            determineDiscontinuityState();
        }

        return discontinuityState;
    }

    public long getDiscontinuityDeterminationHeight() {

        if (discontinuityState == DiscontinuityState.Undetermined) {
            determineDiscontinuityState();
        }

        return discontinuityDeterminationHeight;
    }

    private void determineDiscontinuityState() {

        // Note: this method will not determine the discontinuity state of the Genesis block. It is assigned in the
        // constructor.

        CycleInformation cycleInformation = getCycleInformation();
        if (cycleInformation != null) {
            long discontinuityDeterminationHeight = cycleInformation.getDeterminationHeight();
            if (cycleInformation.isNewVerifier()) {

                // For a new verifier, find the previous new verifier and ensure that the difference is c + 2 from that
                // verifier.
                Block blockToCheck = getPreviousBlock();
                while (blockToCheck != null && blockToCheck.getCycleInformation() != null &&
                        discontinuityState == DiscontinuityState.Undetermined) {

                    discontinuityDeterminationHeight = Math.min(discontinuityDeterminationHeight,
                            blockToCheck.getCycleInformation().getDeterminationHeight());

                    if (blockToCheck.getBlockHeight() == 0L) {
                        discontinuityState = DiscontinuityState.IsNotDiscontinuity;
                        this.discontinuityDeterminationHeight = 0L;
                    } else if (blockToCheck.getCycleInformation().isNewVerifier()) {
                        if (getBlockHeight() - blockToCheck.getBlockHeight() >
                                blockToCheck.getCycleInformation().getCycleLength() + 2) {
                            discontinuityState = DiscontinuityState.IsNotDiscontinuity;
                            this.discontinuityDeterminationHeight = discontinuityDeterminationHeight;
                        } else {
                            discontinuityState = DiscontinuityState.IsDiscontinuity;
                            this.discontinuityDeterminationHeight = discontinuityDeterminationHeight;
                        }
                    }
                    blockToCheck = blockToCheck.getPreviousBlock();

                    if (blockToCheck == null) {
                        System.out.println("new verifier, block is null -- unable to determine state -- " +
                                DebugUtil.callingMethods(3));
                    } else if (blockToCheck.getCycleInformation() == null) {
                        System.out.println("new verifier, cycle is null for height " + blockToCheck.getBlockHeight() +
                                " -- unable to determine state -- " + DebugUtil.callingMethods(3));
                    }
                }

            } else {

                // For an existing verifier, find the previous two locations of that verifier, or just the previous
                // location if the verifier was new in its last location.
                Block blockToCheck = getPreviousBlock();
                Block previousBlockForVerifier = null;
                while (blockToCheck != null && blockToCheck.getCycleInformation() != null &&
                        discontinuityState == DiscontinuityState.Undetermined) {

                    discontinuityDeterminationHeight = Math.min(discontinuityDeterminationHeight,
                            blockToCheck.getCycleInformation().getDeterminationHeight());

                    if (ByteUtil.arraysAreEqual(getVerifierIdentifier(), blockToCheck.getVerifierIdentifier())) {
                        if (blockToCheck.getCycleInformation().isNewVerifier()) {
                            if (getCycleInformation().getCycleLength() >
                                    blockToCheck.getCycleInformation().getCycleLength() / 2) {
                                discontinuityState = DiscontinuityState.IsNotDiscontinuity;
                                this.discontinuityDeterminationHeight = discontinuityDeterminationHeight;
                            } else {
                                discontinuityState = DiscontinuityState.IsDiscontinuity;
                                this.discontinuityDeterminationHeight = discontinuityDeterminationHeight;
                            }
                        } else if (previousBlockForVerifier != null) {
                            long threshold = Math.max(blockToCheck.getCycleInformation().getCycleLength(),
                                    previousBlockForVerifier.getCycleInformation().getCycleLength()) / 2;
                            if (getCycleInformation().getCycleLength() > threshold) {
                                discontinuityState = DiscontinuityState.IsNotDiscontinuity;
                                this.discontinuityDeterminationHeight = discontinuityDeterminationHeight;
                            } else {
                                discontinuityState = DiscontinuityState.IsDiscontinuity;
                                this.discontinuityDeterminationHeight = discontinuityDeterminationHeight;
                            }
                        } else {
                            previousBlockForVerifier = blockToCheck;
                        }
                    } else if (blockToCheck.getBlockHeight() == 0L) {

                        if (previousBlockForVerifier != null) {
                            if (getCycleInformation().getCycleLength() >
                                    previousBlockForVerifier.getCycleInformation().getCycleLength() / 2) {
                                discontinuityState = DiscontinuityState.IsNotDiscontinuity;
                                discontinuityDeterminationHeight = 0L;
                            } else {
                                discontinuityState = DiscontinuityState.IsDiscontinuity;
                                discontinuityDeterminationHeight = 0L;
                            }
                        } else {
                            discontinuityState = DiscontinuityState.IsNotDiscontinuity;
                            discontinuityDeterminationHeight = 0L;
                        }
                    }

                    blockToCheck = blockToCheck.getPreviousBlock();

                    if (blockToCheck == null) {
                        System.out.println("existing verifier, block is null -- unable to determine state -- " +
                                DebugUtil.callingMethods(3));
                    } else if (blockToCheck.getCycleInformation() == null) {
                        System.out.println("existing verifier, cycle is null at height " +
                                blockToCheck.getBlockHeight() + " -- unable to determine state -- " +
                                DebugUtil.callingMethods(3));
                    }
                }
            }
        } else {
            System.out.println("cycle information is null");
        }
    }


    public long getTransactionFees() {

        long fees = 0L;
        for (Transaction transaction : transactions) {
            fees += transaction.getFee();
        }

        return fees;
    }

    public int getByteSize() {

        return getByteSize(true);
    }

    public int getByteSize(boolean includeSignature) {

        int size = FieldByteSize.blockHeight +           // height
                FieldByteSize.hash +                     // previous-block hash
                FieldByteSize.timestamp +                // start timestamp
                FieldByteSize.timestamp +                // verification timestamp
                4 +                                      // number of transactions
                FieldByteSize.hash;                      // balance-list hash
        for (Transaction transaction : transactions) {
            size += transaction.getByteSize();
        }
        if (includeSignature) {
            size += FieldByteSize.identifier + FieldByteSize.signature;
        }

        return size;
    }

    public CycleInformation getCycleInformation() {

        if (cycleInformation == null) {
            cycleInformation = ChainOptionManager.cycleInformationForBlock(this);
        }

        return cycleInformation;
    }

    public byte[] getBytes() {

        return getBytes(true);
    }

    private byte[] getBytes(boolean includeSignature) {

        int size = getByteSize();

        // Assemble the buffer.
        byte[] array = new byte[size];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        buffer.putLong(height);
        buffer.put(previousBlockHash);
        buffer.putLong(startTimestamp);
        buffer.putLong(verificationTimestamp);
        buffer.putInt(transactions.size());
        for (Transaction transaction : transactions) {
            buffer.put(transaction.getBytes());
        }
        buffer.put(balanceListHash);
        if (includeSignature) {
            buffer.put(verifierIdentifier);
            buffer.put(verifierSignature);
        }

        return array;
    }

    public void sign(byte[] signerSeed) {

        this.verificationTimestamp = System.currentTimeMillis();
        this.verifierIdentifier = KeyUtil.identifierForSeed(signerSeed);
        this.verifierSignature = SignatureUtil.signBytes(getBytes(false), signerSeed);
    }

    public boolean signatureIsValid() {
        return SignatureUtil.signatureIsValid(verifierSignature, getBytes(false), verifierIdentifier);
    }

    public static Block fromBytes(byte[] bytes) {

        return fromByteBuffer(ByteBuffer.wrap(bytes));
    }

    public static Block fromByteBuffer(ByteBuffer buffer) {

        long blockHeight = buffer.getLong();
        byte[] previousBlockHash = new byte[FieldByteSize.hash];
        buffer.get(previousBlockHash);
        long startTimestamp = buffer.getLong();
        long verificationTimestamp = buffer.getLong();
        int numberOfTransactions = buffer.getInt();
        List<Transaction> transactions = new ArrayList<>();
        for (int i = 0; i < numberOfTransactions; i++) {
            transactions.add(Transaction.fromByteBuffer(buffer));
        }

        byte[] balanceListHash = new byte[FieldByteSize.hash];
        buffer.get(balanceListHash);
        byte[] verifierIdentifier = new byte[FieldByteSize.identifier];
        buffer.get(verifierIdentifier);
        byte[] verifierSignature = new byte[FieldByteSize.signature];
        buffer.get(verifierSignature);

        return new Block(blockHeight, previousBlockHash, startTimestamp, verificationTimestamp, transactions,
                balanceListHash, verifierIdentifier, verifierSignature);
    }

    public static BalanceList balanceListForNextBlock(Block previousBlock, List<Transaction> transactions,
                                                      byte[] verifierIdentifier) {

        BalanceList result = null;
        try {
            // For the Genesis block, start with an empty balance list, no rollover fees, and an empty list of previous
            // verifiers. For all others, start with the information from the previous block's balance list.
            long blockHeight;
            List<BalanceListItem> previousBalanceItems;
            long previousRolloverFees;
            List<byte[]> previousVerifiers;
            if (previousBlock == null) {
                blockHeight = 0L;
                previousBalanceItems = new ArrayList<>();
                previousRolloverFees = 0L;
                previousVerifiers = new ArrayList<>();
            } else {
                blockHeight = previousBlock.getBlockHeight() + 1L;
                BalanceList previousBalanceList = previousBlock.getBalanceList();
                previousBalanceItems = previousBalanceList.getItems();
                previousRolloverFees = previousBalanceList.getRolloverFees();

                // Get the previous verifiers from the previous block. Add the newest and remove the oldest.
                previousVerifiers = new ArrayList<>(previousBalanceList.getPreviousVerifiers());
                previousVerifiers.add(previousBlock.getVerifierIdentifier());
                if (previousVerifiers.size() > 9) {
                    previousVerifiers.remove(0);
                }
            }

            // Make a map of the identifiers to balances.
            Map<ByteBuffer, Long> identifierToBalanceMap = new HashMap<>();
            for (BalanceListItem item : previousBalanceItems) {
                identifierToBalanceMap.put(ByteBuffer.wrap(item.getIdentifier()), item.getBalance());
            }

            // Add/subtract all transactions.
            long feesThisBlock = 0L;
            for (Transaction transaction : transactions) {
                feesThisBlock += transaction.getFee();
                if (transaction.getType() != Transaction.typeCoinGeneration) {
                    adjustBalance(transaction.getSenderIdentifier(), -transaction.getAmount(), identifierToBalanceMap);
                }
                long amountAfterFee = transaction.getAmount() - transaction.getFee();
                if (amountAfterFee > 0) {
                    adjustBalance(transaction.getReceiverIdentifier(), amountAfterFee, identifierToBalanceMap);
                }
            }

            // Split the transaction fees among the current and previous verifiers.
            List<byte[]> verifiers = new ArrayList<>(previousVerifiers);
            verifiers.add(verifierIdentifier);
            long totalFees = feesThisBlock + previousRolloverFees;
            long feesPerVerifier = totalFees / verifiers.size();
            if (feesPerVerifier > 0L) {
                for (byte[] verifier : verifiers) {
                    adjustBalance(verifier, feesPerVerifier, identifierToBalanceMap);
                }
            }

            // Make the new balance items from the balance map.
            List<BalanceListItem> balanceItems = new ArrayList<>();
            for (ByteBuffer identifier : identifierToBalanceMap.keySet()) {
                long balance = identifierToBalanceMap.get(identifier);
                if (balance > 0L) {
                    balanceItems.add(new BalanceListItem(identifier.array(), balance));
                }
            }

            // Make the balance list. The pairs are sorted in the constructor.
            byte rolloverFees = (byte) (totalFees % verifiers.size());
            result = new BalanceList(blockHeight, rolloverFees, previousVerifiers, balanceItems);

        } catch (Exception ignored) { ignored.printStackTrace(); }

        return result;
    }

    private static void adjustBalance(byte[] identifier, long amount, Map<ByteBuffer, Long> identifierToBalanceMap) {

        ByteBuffer identifierBuffer = ByteBuffer.wrap(identifier);
        Long balance = identifierToBalanceMap.get(identifierBuffer);
        if (balance == null) {
            balance = 0L;
        }
        balance += amount;
        identifierToBalanceMap.put(identifierBuffer, balance);
    }

    public static boolean isValidGenesisBlock(Block block, StringBuilder error) {

        if (error == null) {
            error = new StringBuilder();
        }

        boolean valid = true;
        if (block.getBlockHeight() != 0L) {
            error.append("The block height is " + block.getBlockHeight() + ", but the only valid height for a " +
                    "Genesis block is 0. ");
            valid = false;
        }

        if (block.getTransactions().size() == 1) {
            Transaction transaction = block.getTransactions().get(0);
            if (transaction.getType() != Transaction.typeCoinGeneration) {
                error.append("The only valid transaction type in the Genesis block is coin generation (type " +
                        Transaction.typeCoinGeneration + "). The transaction in this block is of type " +
                        transaction.getType() + ". ");
                valid = false;
            }
            if (transaction.getAmount() != Transaction.micronyzosInSystem) {
                error.append("The Genesis block transaction must be for exactly " +
                        PrintUtil.printAmount(Transaction.micronyzosInSystem) + ". The transaction in this block is " +
                        "for " + PrintUtil.printAmount(transaction.getAmount()) + ". ");
                valid = false;
            }
        } else {
            error.append("The Genesis block must have exactly 1 transaction. This block has " +
                    block.getTransactions().size() + " transactions. ");
            valid = false;
        }

        if (!ByteUtil.arraysAreEqual(block.getVerifierIdentifier(), Block.genesisVerifier)) {
            error.append("The Genesis block must be verified by " +
                    ByteUtil.arrayAsStringWithDashes(Block.genesisVerifier) + ". This block was verified by " +
                    ByteUtil.arrayAsStringWithDashes(block.getVerifierIdentifier()) + ". ");
            valid = false;
        }

        if (!block.signatureIsValid()) {
            error.append("The signature is not valid. ");
            valid = false;
        }

        if (error.length() > 0 && error.charAt(error.length() - 1) == ' ') {
            error.deleteCharAt(error.length() - 1);
        }

        return valid;
    }

    public static void reset() {
        genesisBlockStartTimestamp = -1L;
    }

    public long chainScore(long zeroBlockHeight) {

        // This score is always relative to a provided block height. The zero block height has a score of zero, and
        // each subsequent block affects the score as follows:
        // - a new verifier subtracts 10 but adds the verifier's position in the queue
        // - an existing verifier adds the verifier's position in the cycle multiplied by 10
        // - a discontinuity adds a large penalty score

        long score = 0L;
        Block block = this;
        while (block != null && block.getBlockHeight() > zeroBlockHeight && score < Long.MAX_VALUE) {
            CycleInformation cycleInformation = block.getCycleInformation();
            DiscontinuityState discontinuityState = block.getDiscontinuityState();
            if (cycleInformation == null || discontinuityState == DiscontinuityState.Undetermined) {
                score = Long.MAX_VALUE;  // unable to compute
            } else {
                if (discontinuityState == DiscontinuityState.IsDiscontinuity) {
                    score += 1000000L;
                } else if (cycleInformation.isNewVerifier()) {
                    score -= 10L;
                    // TODO: add the queue index of the new verifier
                } else {
                    score += cycleInformation.getVerifierIndexInCycle() * 10L;
                }
            }

            block = block.getPreviousBlock();
        }

        if (block == null) {
            score = Long.MAX_VALUE;
        }

        return score;
    }
}
