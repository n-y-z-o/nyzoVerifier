package co.nyzo.verifier;

import co.nyzo.verifier.messages.NewVerifierVote;
import co.nyzo.verifier.util.DebugUtil;
import co.nyzo.verifier.util.NotificationUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.SignatureUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class Block implements MessageObject {

    // TODO: ensure verification interval is enforced in both registration and freezing of blocks

    public enum DiscontinuityState {
        Undetermined,
        IsDiscontinuity,
        IsNotDiscontinuity
    }

    public static final byte[] genesisVerifier = ByteUtil.byteArrayFromHexString("6b32332d4b28e6ad-" +
            "d7b8f86f374045ca-fc6453344a1c47b6-feaf485f8c2e0d47", 32);

    public static final byte[] genesisBlockHash = HashUtil.doubleSHA256(new byte[0]);

    public static final long blockDuration = 5000L;
    public static final long minimumVerificationInterval = 2000L;

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
    private DiscontinuityState discontinuityState;

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
        this.discontinuityState = height == 0 ? DiscontinuityState.IsNotDiscontinuity : DiscontinuityState.Undetermined;

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
        this.discontinuityState = height == 0 ? DiscontinuityState.IsNotDiscontinuity : DiscontinuityState.Undetermined;
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

        if (balanceList == null) {
            Block previousBlock = getPreviousBlock();
            if (previousBlock != null || height == 0L) {
                setBalanceList(balanceListForNextBlock(previousBlock, transactions, verifierIdentifier));
            }
        }

        return balanceList;
    }

    public void setBalanceList(BalanceList balanceList) {

        if (balanceList != null) {
            if (ByteUtil.arraysAreEqual(HashUtil.doubleSHA256(balanceList.getBytes()), balanceListHash)) {
                this.balanceList = balanceList;
            } else {
                NotificationUtil.send("balance list does not match hash on " + Verifier.getNickname() + " - (h=" +
                        balanceList.getBlockHeight() + ") " + DebugUtil.callingMethods(7));
            }
        }
    }

    public Block getPreviousBlock() {

        Block previousBlock = null;
        if (getBlockHeight() > 0L) {
            if (getBlockHeight() <= BlockManager.frozenEdgeHeight() + 1) {
                Block frozenBlock = BlockManager.frozenBlockForHeight(height - 1);
                if (frozenBlock != null && ByteUtil.arraysAreEqual(frozenBlock.getHash(), previousBlockHash)) {
                    previousBlock = frozenBlock;
                }
            } else {
                previousBlock = ChainOptionManager.unfrozenBlockAtHeight(height - 1, previousBlockHash);
            }
        }

        return previousBlock;
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

    private void determineDiscontinuityState() {

        // Note: this method will not determine the discontinuity state of the Genesis block. It is assigned in the
        // constructor.

        CycleInformation cycleInformation = getCycleInformation();
        if (cycleInformation != null) {
            if (cycleInformation.isNewVerifier()) {

                // For a new verifier, find the previous new verifier and ensure that the difference is c + 2 from that
                // verifier. If a new verifier is not found far enough back in the chain, we are certain that there is
                // no discontinuity. The c * 2 + 2 calculation is a safe over-approximation, considering that one block
                // back, the cycle length could be roughly double the current cycle length and still not result in a
                // discontinuity.
                Block blockToCheck = getPreviousBlock();
                while (blockToCheck != null && blockToCheck.getCycleInformation() != null &&
                        discontinuityState == DiscontinuityState.Undetermined) {

                    if (blockToCheck.getBlockHeight() == 0L) {
                        discontinuityState = DiscontinuityState.IsNotDiscontinuity;
                    } else if (blockToCheck.getCycleInformation().isNewVerifier()) {
                        if (getBlockHeight() - blockToCheck.getBlockHeight() >=
                                blockToCheck.getCycleInformation().getCycleLength() + 2) {
                            discontinuityState = DiscontinuityState.IsNotDiscontinuity;
                        } else {
                            discontinuityState = DiscontinuityState.IsDiscontinuity;
                        }
                    } else if (getBlockHeight() - blockToCheck.getBlockHeight() >
                            blockToCheck.getCycleInformation().getCycleLength() * 2 + 2) {
                        discontinuityState = DiscontinuityState.IsNotDiscontinuity;
                    }
                    blockToCheck = blockToCheck.getPreviousBlock();

                    if (discontinuityState == DiscontinuityState.Undetermined) {
                        if (blockToCheck == null) {
                            System.out.println("new verifier, block is null -- unable to determine state for block " +
                                    height + " -- " + DebugUtil.callingMethods(3));
                        } else if (blockToCheck.getCycleInformation() == null) {
                            System.out.println("new verifier, cycle is null for height " +
                                    blockToCheck.getBlockHeight() + " -- unable to determine state for block " +
                                    height + " -- " + DebugUtil.callingMethods(3));
                        }
                    }
                }

            } else {

                // For an existing verifier, find the previous two locations of that verifier, or just the previous
                // location if the verifier was new in its last location.
                Block blockToCheck = getPreviousBlock();
                Block previousBlockForVerifier = null;
                while (blockToCheck != null && blockToCheck.getCycleInformation() != null &&
                        discontinuityState == DiscontinuityState.Undetermined) {

                    if (ByteUtil.arraysAreEqual(getVerifierIdentifier(), blockToCheck.getVerifierIdentifier())) {
                        if (blockToCheck.getCycleInformation().isNewVerifier()) {
                            if (getCycleInformation().getCycleLength() >
                                    blockToCheck.getCycleInformation().getCycleLength() / 2) {
                                discontinuityState = DiscontinuityState.IsNotDiscontinuity;
                            } else {
                                discontinuityState = DiscontinuityState.IsDiscontinuity;
                            }
                        } else if (previousBlockForVerifier != null) {
                            long threshold = Math.max(blockToCheck.getCycleInformation().getCycleLength(),
                                    previousBlockForVerifier.getCycleInformation().getCycleLength()) / 2;
                            if (getCycleInformation().getCycleLength() > threshold) {
                                discontinuityState = DiscontinuityState.IsNotDiscontinuity;
                            } else {
                                discontinuityState = DiscontinuityState.IsDiscontinuity;
                            }
                        } else {
                            previousBlockForVerifier = blockToCheck;
                        }
                    } else if (blockToCheck.getBlockHeight() == 0L) {

                        if (previousBlockForVerifier != null) {
                            if (getCycleInformation().getCycleLength() >
                                    previousBlockForVerifier.getCycleInformation().getCycleLength() / 2) {
                                discontinuityState = DiscontinuityState.IsNotDiscontinuity;
                            } else {
                                discontinuityState = DiscontinuityState.IsDiscontinuity;
                            }
                        } else {
                            discontinuityState = DiscontinuityState.IsNotDiscontinuity;
                        }
                    }

                    blockToCheck = blockToCheck.getPreviousBlock();

                    if (discontinuityState == DiscontinuityState.Undetermined) {
                        if (blockToCheck == null) {
                            System.out.println("existing verifier, block is null -- unable to determine state for " +
                                    "height " + height + " -- " + DebugUtil.callingMethods(3));
                        } else if (blockToCheck.getCycleInformation() == null) {
                            System.out.println("existing verifier, cycle is null at height " +
                                    blockToCheck.getBlockHeight() + " -- unable to determine state for height " +
                                    height + " -- " + DebugUtil.callingMethods(3));
                        }
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
            List<BalanceListItem> previousBalanceItems = null;
            long previousRolloverFees = -1;
            List<byte[]> previousVerifiers = null;
            if (previousBlock == null) {
                blockHeight = 0L;
                previousBalanceItems = new ArrayList<>();
                previousRolloverFees = 0L;
                previousVerifiers = new ArrayList<>();
            } else {
                blockHeight = previousBlock.getBlockHeight() + 1L;
                BalanceList previousBalanceList = previousBlock.getBalanceList();
                if (previousBalanceList != null) {
                    previousBalanceItems = previousBalanceList.getItems();
                    previousRolloverFees = previousBalanceList.getRolloverFees();

                    // Get the previous verifiers from the previous block. Add the newest and remove the oldest.
                    previousVerifiers = new ArrayList<>(previousBalanceList.getPreviousVerifiers());
                    previousVerifiers.add(previousBlock.getVerifierIdentifier());
                    if (previousVerifiers.size() > 9) {
                        previousVerifiers.remove(0);
                    }
                }
            }

            // Only continue if we have the necessary data.
            if (previousBalanceItems != null && previousRolloverFees >= 0) {
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
                        adjustBalance(transaction.getSenderIdentifier(), -transaction.getAmount(),
                                identifierToBalanceMap);
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
            }
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
    
    public long chainScore(long zeroBlockHeight) {

        // This score is always relative to a provided block height. The zero block height has a score of zero, and
        // each subsequent block affects the score as follows:
        // - a new verifier subtracts 3 but adds 2 times the verifier's position in the queue
        // - an existing verifier adds the verifier's position in the cycle multiplied by 2
        // - a discontinuity adds a large penalty score

        long score = 0L;
        Block block = this;
        while (block != null && block.getBlockHeight() > zeroBlockHeight && score < Long.MAX_VALUE - 1) {
            CycleInformation cycleInformation = block.getCycleInformation();
            DiscontinuityState discontinuityState = block.getDiscontinuityState();
            if (cycleInformation == null || discontinuityState == DiscontinuityState.Undetermined) {
                score = Long.MAX_VALUE - 1;  // unable to compute; might improve with more information
            } else {
                if (discontinuityState == DiscontinuityState.IsDiscontinuity) {
                    score = Long.MAX_VALUE;  // invalid
                } else if (cycleInformation.isNewVerifier()) {
                    score -= 3L;

                    List<NewVerifierVote> topNewVerifiers = NewVerifierVoteManager.topVerifiers();
                    /*
                    ByteBuffer verifierIdentifier = ByteBuffer.wrap(block.getVerifierIdentifier());
                    int indexInQueue = topNewVerifiers.indexOf(verifierIdentifier);
                    if (indexInQueue < 0) {
                        score = Long.MAX_VALUE - 1;
                    } else {
                        score += indexInQueue * 2L;
                    }*/
                    score = Long.MAX_VALUE - 1;
                } else {
                    score += cycleInformation.getBlockVerifierIndexInCycle() * 2L;
                }
            }

            block = block.getPreviousBlock();
        }

        if (block == null) {
            score = Long.MAX_VALUE;
        }

        return score;
    }

    @Override
    public String toString() {
        return "[Block: height=" + getBlockHeight() + ", hash=" + PrintUtil.compactPrintByteArray(getHash()) + "]";
    }
}
