package co.nyzo.verifier;

import co.nyzo.verifier.util.LogUtil;
import co.nyzo.verifier.util.PreferencesUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MetadataManager {

    // Participation in this system is voluntary, but it is active by default.
    private static final String addMetadataTransactionsKey = "verifier_add_metadata_transactions";
    private static final boolean addMetadataTransactions = PreferencesUtil.getBoolean(addMetadataTransactionsKey, true);

    private static final String nicknameMetadataKey = "nickname";
    private static final String topNewVerifierMetadataKey = "top-new-verifier";

    public static List<Transaction> metadataTransactions(Block previousBlock) {

        // Get the previous balance list. The transactions will only be added if this verifier has sufficient funds to
        // add them. This check errs on the side of caution.
        BalanceList previousBalanceList = BalanceListManager.balanceListForBlock(previousBlock);

        // Make the list. If this verifier is not configured to add metadata transactions, or if information or the
        // verifier's balance are insufficient, the list remains empty. The 100-micronyzo offset is an arbitrary small
        // offset to guarantee safety even with a large number of metadata transactions.
        List<Transaction> transactions = new ArrayList<>();
        if (addMetadataTransactions && previousBalanceList != null &&
                previousBalanceList.balanceForIdentifier(Verifier.getIdentifier()) >
                        BalanceManager.minimumPreferredBalance + 100L) {
            Transaction[] transactionsToAdd = {
                    nicknameTransaction(previousBlock),
                    topNewVerifierTransaction(previousBlock)
            };

            for (Transaction transaction : transactionsToAdd) {
                if (transaction != null) {
                    transactions.add(transaction);
                }
            }
        }

        return transactions;
    }

    public static void registerBlock(Block block) {

        // This entire method is in a try/catch to ensure that unexpected issues processing metadata transactions do
        // not become vulnerabilities.
        try {
            // Get all metadata transactions placed in the block by the block verifier.
            for (Transaction transaction : block.getTransactions()) {
                if (ByteUtil.arraysAreEqual(block.getVerifierIdentifier(), transaction.getSenderIdentifier())) {
                    MetadataItem metadataItem = MetadataItem.fromTransaction(transaction);
                    if (metadataItem != null) {
                        if (metadataItem.getKey().equals(nicknameMetadataKey)) {
                            processNicknameTransaction(transaction);
                        } else if (metadataItem.getKey().equals(topNewVerifierMetadataKey)) {
                            processTopNewVerifierTransaction(transaction);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.println("exception processing metadata transactions: " + PrintUtil.printException(e));
        }
    }

    private static Transaction nicknameTransaction(Block previousBlock) {

        Transaction transaction = null;

        // Check the local nickname and the nickname stored on the blockchain. If the local nickname is valid and
        // the local nickname differs from the on-chain nickname, create a metadata transaction for the nickname.
        String localNickname = NicknameManager.getLocalNickname();
        String onChainNickname = NicknameManager.getOnChainNickname();
        if (localNickname != null && !localNickname.isEmpty() && !localNickname.equals(onChainNickname)) {
            // Timestamp the metadata for the beginning of this block. The length check relative to identifier field
            // size is semantically correct due to the use of the receiver identifier field for storage.
            long timestamp = previousBlock.getStartTimestamp() + Block.blockDuration;
            byte[] nicknameBytes = localNickname.getBytes(StandardCharsets.UTF_8);
            if (nicknameBytes.length > FieldByteSize.identifier) {
                nicknameBytes = Arrays.copyOf(nicknameBytes, FieldByteSize.identifier);
            }

            // Make the metadata item and use it to generate the transaction.
            MetadataItem item = new MetadataItem(timestamp, nicknameMetadataKey, Verifier.getIdentifier(), null,
                    nicknameBytes);
            transaction = item.generateTransaction(previousBlock);
        }

        return transaction;
    }

    private static void processNicknameTransaction(Transaction transaction) {

        // Build the nickname from the receiver identifier. Trimming the string removes trailing null characters due to
        // zero padding.
        String nickname = new String(transaction.getReceiverIdentifier()).trim();
        if (!nickname.isEmpty()) {
            // Store the nickname in the manager map.
            NicknameManager.put(transaction.getSenderIdentifier(), nickname);

            // If this nickname is for this verifier, store it as the on-chain nickname.
            if (ByteUtil.arraysAreEqual(transaction.getSenderIdentifier(), Verifier.getIdentifier())) {
                NicknameManager.setOnChainNickname(nickname);
            }
        }
    }

    private static Transaction topNewVerifierTransaction(Block previousBlock) {

        Transaction transaction = null;

        // Get the local and on-chain top verifiers. Replace null values with arrays of zeros.
        ByteBuffer localTopVerifierBuffer = NewVerifierVoteManager.topVerifier();
        byte[] localTopVerifier = localTopVerifierBuffer == null ? new byte[FieldByteSize.identifier] :
                localTopVerifierBuffer.array();
        byte[] onChainTopVerifier = NewVerifierVoteManager.getOnChainTopVerifier();
        if (onChainTopVerifier == null) {
            onChainTopVerifier = new byte[FieldByteSize.identifier];
        }

        // If the values are different, create a transaction. This will create a transaction if the source values are
        // different, and it will create a transaction if either source value is null but the other is not.
        if (!ByteUtil.arraysAreEqual(localTopVerifier, onChainTopVerifier)) {
            long timestamp = previousBlock.getStartTimestamp() + Block.blockDuration;
            MetadataItem item = new MetadataItem(timestamp, topNewVerifierMetadataKey, Verifier.getIdentifier(), null,
                    localTopVerifier);
            transaction = item.generateTransaction(previousBlock);
        }

        return transaction;
    }

    private static void processTopNewVerifierTransaction(Transaction transaction) {
        NewVerifierVoteManager.setOnChainTopVerifier(transaction.getReceiverIdentifier());
    }
}
