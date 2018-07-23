package co.nyzo.verifier;

import co.nyzo.verifier.util.DebugUtil;
import co.nyzo.verifier.util.NotificationUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.SignatureUtil;

import java.nio.ByteBuffer;
import java.util.*;

public class Block implements MessageObject {

    public enum ContinuityState {
        Undetermined,
        Discontinuous,
        Continuous
    }

    public static final byte[] genesisVerifier = ByteUtil.byteArrayFromHexString("6b32332d4b28e6ad-" +
            "d7b8f86f374045ca-fc6453344a1c47b6-feaf485f8c2e0d47", 32);

    public static final byte[] genesisBlockHash = HashUtil.doubleSHA256(new byte[0]);

    public static final long blockDuration = 5000L;
    public static final long minimumVerificationInterval = 1500L;
    public static final short blocksBetweenFee = 100;

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
    private byte[] verifierIdentifier;             // 32 bytes
    private byte[] verifierSignature;              // 64 bytes

    private ContinuityState continuityState = ContinuityState.Undetermined;
    private CycleInformation cycleInformation = null;

    public Block(long height, byte[] previousBlockHash, long startTimestamp, List<Transaction> transactions,
                 byte[] balanceListHash) {

        this.height = height;
        this.previousBlockHash = previousBlockHash;
        this.startTimestamp = startTimestamp;
        this.verificationTimestamp = System.currentTimeMillis();
        this.transactions = new ArrayList<>(transactions);
        this.balanceListHash = balanceListHash;

        try {
            this.verifierIdentifier = Verifier.getIdentifier();
            this.verifierSignature = Verifier.sign(getBytes(false));
        } catch (Exception e) {
            this.verifierIdentifier = new byte[32];
            this.verifierSignature = new byte[64];
        }
    }

    public Block(long height, byte[] previousBlockHash, long startTimestamp, long verificationTimestamp,
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

    public Block getPreviousBlock() {

        Block previousBlock = null;
        if (getBlockHeight() > 0L) {
            if (getBlockHeight() <= BlockManager.getFrozenEdgeHeight() + 1) {
                Block frozenBlock = BlockManager.frozenBlockForHeight(height - 1);
                if (frozenBlock != null && ByteUtil.arraysAreEqual(frozenBlock.getHash(), previousBlockHash)) {
                    previousBlock = frozenBlock;
                }
            } else {
                previousBlock = UnfrozenBlockManager.unfrozenBlockAtHeight(height - 1, previousBlockHash);
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

    public ContinuityState getContinuityState() {

        if (continuityState == ContinuityState.Undetermined) {
            determineContinuityState();
        }

        return continuityState;
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
            calculateCycleInformation();
        }

        return cycleInformation;
    }

    private void calculateCycleInformation() {

        // Make a list of sets for the last four cycles.
        List<Set<ByteBuffer>> cycles = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            cycles.add(new HashSet<>());
        }

        // Starting at this block, stepping backward in the chain, build the last four cycles.
        int index = 0;
        Block blockToCheck = this;
        boolean reachedGenesisBlock = false;
        while (index < 4 && blockToCheck != null) {

            Set<ByteBuffer> cycle = cycles.get(index);
            ByteBuffer identifier = ByteBuffer.wrap(blockToCheck.getVerifierIdentifier());
            if (cycle.contains(identifier)) {
                index++;
            } else {
                cycle.add(identifier);
                reachedGenesisBlock = blockToCheck.getBlockHeight() == 0L;
                blockToCheck = blockToCheck.getPreviousBlock();
            }
        }

        // If we found four full cycles or if we reached the beginning of the chain, we can build the
        // cycle information.
        if (index == 4 || reachedGenesisBlock) {

            ByteBuffer verifierIdentifier = ByteBuffer.wrap(getVerifierIdentifier());

            boolean newVerifier = !cycles.get(1).contains(verifierIdentifier);
            boolean genesisCycle = cycles.get(1).isEmpty();

            int[] cycleLengths = { cycles.get(0).size(), cycles.get(1).size(), cycles.get(2).size(),
                    cycles.get(3).size() };
            cycleInformation = new CycleInformation(height, cycleLengths, newVerifier, genesisCycle);
        }
    }

    private void determineContinuityState() {

        CycleInformation cycleInformation = getCycleInformation();
        if (cycleInformation != null) {

            // Proof-of-diversity rule 1: After the first existing verifier in the block chain, a new verifier is only
            // allowed if none of the other blocks in the cycle, the previous cycle, or the two blocks before the
            // previous cycle were verified by new verifiers.

            boolean rule1Pass;
            boolean sufficientInformation;
            if (cycleInformation.isGenesisCycle() || !cycleInformation.isNewVerifier()) {
                rule1Pass = true;
                sufficientInformation = true;
            } else {
                long startCheckHeight = getBlockHeight() - cycleInformation.getCycleLength() -
                        cycleInformation.getCycleLength(1) - 1;
                Block blockToCheck = getPreviousBlock();
                sufficientInformation = blockToCheck != null;
                rule1Pass = true;
                while (blockToCheck != null && blockToCheck.getBlockHeight() >= startCheckHeight && rule1Pass &&
                        sufficientInformation) {

                    // If the cycle information is null, the continuity state cannot be calculated. If the block is a
                    // new verifier, this block is discontinuous.
                    if (blockToCheck.getCycleInformation() == null) {
                        sufficientInformation = false;
                    } else if (blockToCheck.getCycleInformation().isNewVerifier()) {
                        rule1Pass = false;
                    }

                    // If we have not reached the start check height and the next block back in the chain is null, the
                    // continuity state cannot be calculated.
                    if (blockToCheck.getBlockHeight() > startCheckHeight && blockToCheck.getPreviousBlock() == null) {
                        sufficientInformation = false;
                    }

                    // Step back one block.
                    blockToCheck = blockToCheck.getPreviousBlock();
                }
            }

            if (sufficientInformation) {

                if (rule1Pass) {

                    // Proof-of-diversity rule 2: All cycles must be longer than one more than half of the maximum of
                    // the lengths of the three previous cycles.

                    int maximumPreviousLength = Math.max(cycleInformation.getCycleLength(1),
                            Math.max(cycleInformation.getCycleLength(2), cycleInformation.getCycleLength(3)));
                    long threshold = (maximumPreviousLength + 1L) / 2L;
                    boolean rule2Pass = cycleInformation.getCycleLength() > threshold;
                    continuityState = rule2Pass ? ContinuityState.Continuous : ContinuityState.Discontinuous;

                } else {
                    continuityState = ContinuityState.Discontinuous;
                }
            }
        }
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

    public void sign(long verificationTimestamp, byte[] signerSeed) {

        this.verificationTimestamp = verificationTimestamp;
        this.verifierIdentifier = KeyUtil.identifierForSeed(signerSeed);
        this.verifierSignature = SignatureUtil.signBytes(getBytes(false), signerSeed);
    }

    public void sign(byte[] signerSeed) {

        sign(System.currentTimeMillis(), signerSeed);
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

    public static BalanceList balanceListForNextBlock(Block previousBlock, BalanceList previousBalanceList,
                                                      List<Transaction> transactions, byte[] verifierIdentifier) {

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
                // Make a map of the identifiers to balance list items.
                Map<ByteBuffer, BalanceListItem> identifierToItemMap = new HashMap<>();
                for (BalanceListItem item : previousBalanceItems) {
                    identifierToItemMap.put(ByteBuffer.wrap(item.getIdentifier()), item);
                }

                // Add/subtract all transactions.
                long feesThisBlock = 0L;
                for (Transaction transaction : transactions) {
                    feesThisBlock += transaction.getFee();
                    if (transaction.getType() != Transaction.typeCoinGeneration) {
                        adjustBalance(transaction.getSenderIdentifier(), -transaction.getAmount(), identifierToItemMap);
                    }
                    long amountAfterFee = transaction.getAmount() - transaction.getFee();
                    if (amountAfterFee > 0) {
                        adjustBalance(transaction.getReceiverIdentifier(), amountAfterFee, identifierToItemMap);
                    }
                }

                // Subtract fees for all balance list items that owe fees and whose values are greater than zero.
                long periodicAccountFees = 0L;
                for (ByteBuffer identifier : identifierToItemMap.keySet()) {
                    BalanceListItem item = identifierToItemMap.get(identifier);
                    if (item.getBlocksUntilFee() == 0 && item.getBalance() > 0L) {
                        periodicAccountFees++;
                        identifierToItemMap.put(identifier, item.adjustByAmount(-1L));
                    }
                }

                // Split the transaction fees among the current and previous verifiers.
                List<byte[]> verifiers = new ArrayList<>(previousVerifiers);
                verifiers.add(verifierIdentifier);
                long totalFees = feesThisBlock + previousRolloverFees + periodicAccountFees;
                long feesPerVerifier = totalFees / verifiers.size();
                if (feesPerVerifier > 0L) {
                    for (byte[] verifier : verifiers) {
                        adjustBalance(verifier, feesPerVerifier, identifierToItemMap);
                    }
                }

                // Make the new balance items from the balance map, decrementing the blocks-until-fee counter for each.
                List<BalanceListItem> balanceItems = new ArrayList<>();
                for (ByteBuffer identifier : identifierToItemMap.keySet()) {
                    BalanceListItem item = identifierToItemMap.get(identifier);
                    if (item.getBalance() > 0L) {
                        balanceItems.add(item.decrementBlocksUntilFee());
                    }
                }

                // Make the balance list. The pairs are sorted in the constructor.
                byte rolloverFees = (byte) (totalFees % verifiers.size());
                result = new BalanceList(blockHeight, rolloverFees, previousVerifiers, balanceItems);
            }
        } catch (Exception ignored) { ignored.printStackTrace(); }

        return result;
    }

    private static void adjustBalance(byte[] identifier, long amount,
                                      Map<ByteBuffer, BalanceListItem> identifierToItemMap) {

        ByteBuffer identifierBuffer = ByteBuffer.wrap(identifier);
        BalanceListItem item = identifierToItemMap.get(identifierBuffer);
        if (item == null) {
            item = new BalanceListItem(identifier, 0L, Block.blocksBetweenFee);
        }
        item = item.adjustByAmount(amount);
        identifierToItemMap.put(identifierBuffer, item);
    }
    
    public long chainScore(long zeroBlockHeight) {

        // This score is always relative to a provided block height. The zero block height has a score of zero, and
        // each subsequent block affects the score as follows:
        // - a new verifier subtracts 6 but adds 4 times the verifier's position in the queue
        // - an existing verifier adds the verifier's position in the cycle multiplied by 4
        // - an existing verifier that is no longer in the mesh or shares an IP with another verifier adds 5

        long score = 0L;
        Block block = this;
        while (block != null && block.getBlockHeight() > zeroBlockHeight && score < Long.MAX_VALUE - 1) {
            CycleInformation cycleInformation = block.getCycleInformation();
            ContinuityState continuityState = block.getContinuityState();
            if (cycleInformation == null || continuityState == ContinuityState.Undetermined) {
                score = Long.MAX_VALUE - 1;  // unable to compute; might improve with more information
            } else {
                if (continuityState == ContinuityState.Discontinuous) {
                    score = Long.MAX_VALUE;  // invalid
                } else if (cycleInformation.isNewVerifier()) {
                    if (cycleInformation.isGenesisCycle()) {

                        // This is a special case for the Genesis cycle. We want a deterministic order that can be
                        // calculated locally, but we do not care what that order is.
                        score = -1L * Math.abs(HashUtil.longSHA256(block.getVerifierIdentifier()));

                    } else {
                        score -= 6L;

                        List<ByteBuffer> topNewVerifiers = NewVerifierVoteManager.topVerifiers();
                        ByteBuffer verifierIdentifier = ByteBuffer.wrap(block.getVerifierIdentifier());
                        int indexInQueue = topNewVerifiers.indexOf(verifierIdentifier);
                        if (indexInQueue < 0) {
                            score += 12L;  // maximum of three new in queue; this is behind the queue
                        } else {
                            score += indexInQueue * 4L;
                        }
                    }
                } else {
                    Block previousBlock = getPreviousBlock();
                    if (previousBlock == null || previousBlock.getCycleInformation() == null) {
                        score = Long.MAX_VALUE - 1;  // unable to compute; might improve with more information
                    } else {
                        score += (previousBlock.getCycleInformation().getCycleLength() -
                                cycleInformation.getCycleLength()) * 4L;
                    }

                    if (!NodeManager.isActive(verifierIdentifier)) {
                        score += 5L;
                    }
                }
            }

            // Check the verification timestamp interval.
            Block previousBlock = block.getPreviousBlock();
            if (previousBlock != null && previousBlock.getVerificationTimestamp() >
                    block.getVerificationTimestamp() - minimumVerificationInterval) {
                score = Long.MAX_VALUE;  // invalid
            }

            block = previousBlock;
        }

        if (block == null) {
            score = Long.MAX_VALUE - 1;  // unable to compute; might improve with more information
        }

        return score;
    }

    @Override
    public String toString() {
        return "[Block: height=" + getBlockHeight() + ", hash=" + PrintUtil.compactPrintByteArray(getHash()) + "]";
    }
}
