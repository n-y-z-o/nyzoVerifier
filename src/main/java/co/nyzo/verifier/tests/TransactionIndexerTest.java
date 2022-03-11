package co.nyzo.verifier.tests;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.TransactionIndexer;
import co.nyzo.verifier.util.PrintUtil;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class TransactionIndexerTest implements NyzoTest {

    private String failureCause = null;

    // These are artificial sender and receiver identifiers.
    private static final byte[] senderIdentifier = ByteUtil.byteArrayFromHexString("aa", FieldByteSize.identifier);
    private static final byte[] receiverIdentifier = ByteUtil.byteArrayFromHexString("bb", FieldByteSize.identifier);

    public static void main(String[] args) {

        TransactionIndexerTest test = new TransactionIndexerTest();
        boolean successful = test.run();

        if (!successful) {
            System.out.println(TestUtil.failureCause(test.getFailureCause()));
        }
    }

    public boolean run() {

        boolean successful = true;
        try {
            // Get the paths of the index files. Using Java's reflection allows testing without unnecessary additions
            // to the TransactionIndexManager interface.
            Class<TransactionIndexer> transactionIndexerClass = TransactionIndexer.class;
            Method indexFileMethod = transactionIndexerClass.getDeclaredMethod("indexFileForAccount",
                    (new byte[0]).getClass());
            indexFileMethod.setAccessible(true);
            File indexFileSender = (File) indexFileMethod.invoke(transactionIndexerClass, (Object) senderIdentifier);
            File indexFileReceiver = (File) indexFileMethod.invoke(transactionIndexerClass,
                    (Object) receiverIdentifier);

            // Get the paths of the list files using the same mechanism.
            Method listFileMethod = transactionIndexerClass.getDeclaredMethod("listFileForAccount",
                    (new byte[0]).getClass());
            listFileMethod.setAccessible(true);
            File listFileSender = (File) listFileMethod.invoke(transactionIndexerClass, (Object) senderIdentifier);
            File listFileReceiver = (File) listFileMethod.invoke(transactionIndexerClass, (Object) receiverIdentifier);

            // Delete all files for a clean test.
            indexFileSender.delete();
            indexFileReceiver.delete();
            listFileSender.delete();
            listFileReceiver.delete();

            // Create and register the test transactions.
            List<Transaction> transactions = createAndRegisterTransactions();

            // Check the index files.
            successful = checkIndexFile(indexFileSender, transactions, "sender", (byte) 1);
            if (successful) {
                successful = checkIndexFile(indexFileReceiver, transactions, "receiver", (byte) 0);
            }

            // Check the list files.
            if (successful) {
                successful = checkListFile(listFileSender, transactions, "sender");
            }
            if (successful) {
                successful = checkListFile(listFileReceiver, transactions, "receiver");
            }

            // Check the transaction lookup for both accounts.
            if (successful) {
                successful = checkTransactionLookup(transactions, senderIdentifier, "sender");
            }
            if (successful) {
                successful = checkTransactionLookup(transactions, receiverIdentifier, "receiver");
            }


        } catch (Exception e) {
            failureCause = "exception in TransactionIndexManagerTest: " + PrintUtil.printException(e);
            successful = false;
        }

        System.out.println(TestUtil.passFail(successful));

        return successful;
    }

    private static List<Transaction> createAndRegisterTransactions() {

        // Create 10 transactions. The Random object is initialized with a fixed seed for reproducibility. The values
        // generated are far outside the range of valid values.
        List<Transaction> transactions = new ArrayList<>();
        Random random = new Random(57);
        for (int i = 0; i < 10; i++) {
            long timestamp = random.nextLong();
            long amount = random.nextLong();
            long previousHashHeight = random.nextLong();
            byte[] previousBlockHash = new byte[FieldByteSize.hash];
            random.nextBytes(previousBlockHash);
            byte[] senderData = new byte[random.nextInt(32)];
            random.nextBytes(senderData);
            byte[] signature = new byte[FieldByteSize.signature];
            random.nextBytes(signature);

            transactions.add(Transaction.standardTransaction(timestamp, amount, receiverIdentifier,
                    previousHashHeight, previousBlockHash, senderIdentifier, senderData, signature));
        }

        // Create a block from the transactions and register it.
        Block block = new Block(0, 0, new byte[FieldByteSize.hash], 0, transactions, new byte[FieldByteSize.hash]);
        TransactionIndexer.indexTransactionsForBlock(block);

        return transactions;
    }

    private boolean checkIndexFile(File file, List<Transaction> transactions, String senderLabel,
                                   byte expectedSenderByte) {

        // In the index file, transactions are listed on ascending timestamp.
        List<Transaction> sortedTransactions = new ArrayList<>();
        sortedTransactions.sort(new Comparator<Transaction>() {
            @Override
            public int compare(Transaction transaction1, Transaction transaction2) {
                return Long.compare(transaction1.getTimestamp(), transaction2.getTimestamp());
            }
        });

        boolean successful = true;
        try {
            byte[] fileArray = Files.readAllBytes(Paths.get(file.getAbsolutePath()));
            ByteBuffer fileBuffer = ByteBuffer.wrap(fileArray);
            int expectedOffset = 0;
            for (int i = 0; i < sortedTransactions.size() && successful; i++) {
                // Check the timestamp.
                Transaction transaction = transactions.get(i);
                long fileTimestamp = fileBuffer.getLong();
                if (fileTimestamp != transaction.getTimestamp()) {
                    successful = false;
                    failureCause = "in checkIndexFile(), timestamp for " + senderLabel + ", transaction " + i +
                            ", expected value=" + transaction.getTimestamp() + ", file value=" + fileTimestamp;
                }

                // Check the amount.
                long fileAmount = fileBuffer.getLong();
                if (successful && fileAmount != transaction.getAmount()) {
                    successful = false;
                    failureCause = "in checkIndexFile(), amount for " + senderLabel + ", transaction " + i +
                            ", expected value=" + PrintUtil.printAmount(fileAmount) + ", file value=" +
                            PrintUtil.printAmount(transaction.getAmount());
                }

                // Check the sender data.
                byte[] fileSenderData = Message.getByteArray(fileBuffer, 8);
                byte[] expectedSenderData = transaction.getSenderData();
                for (int k = 0; k < Math.min(fileSenderData.length, expectedSenderData.length) && successful; k++) {
                    if (fileSenderData[k] != expectedSenderData[k]) {
                        successful = false;
                        failureCause = "in checkIndexFile(), sender data for " + senderLabel + ", transaction " + i +
                                ", expected value=" + ByteUtil.arrayAsStringNoDashes(expectedSenderData) +
                                ", file value=" + ByteUtil.arrayAsStringNoDashes(fileSenderData);
                    }
                }

                // Check the sender byte.
                if (successful) {
                    byte fileSenderByte = fileBuffer.get();
                    if (fileSenderByte != expectedSenderByte) {
                        successful = false;
                        failureCause = "in checkIndexFile(), sender byte for " + senderLabel + ", transaction " + i +
                                ", expected value=" + expectedSenderByte + ", file value=" + fileSenderByte;
                    }
                }
            }
        } catch (Exception e) {
            failureCause = "exception in TransactionIndexManagerTest: " + PrintUtil.printException(e);
            successful = false;
        }

        return successful;
    }

    private boolean checkListFile(File file, List<Transaction> transactions, String senderLabel) {

        // The transactions are in the list file in the order they were added, so no sorting of the input transaction
        // list is necessary.

        boolean successful = true;
        try {
            // Read the file into an array and wrap it in a buffer. Check all transactions.
            byte[] fileArray = Files.readAllBytes(Paths.get(file.getAbsolutePath()));
            ByteBuffer fileBuffer = ByteBuffer.wrap(fileArray);
            for (int i = 0; i < transactions.size() && successful; i++) {

                // Check the transaction bytes.
                Transaction transaction = transactions.get(i);
                byte[] expectedBytes = transaction.getBytes();
                byte[] fileBytes = Message.getByteArray(fileBuffer, expectedBytes.length);
                if (!ByteUtil.arraysAreEqual(expectedBytes, fileBytes)) {
                    successful = false;
                    failureCause = "transaction bytes for " + senderLabel + ", transaction " + i +
                            ", expected value=" + ByteUtil.arrayAsStringWithDashes(expectedBytes) +
                            ", file value=" + ByteUtil.arrayAsStringWithDashes(fileBytes);
                }
            }
        } catch (Exception e) {
            failureCause = "exception in TransactionIndexManagerTest: " + PrintUtil.printException(e);
            successful = false;
        }

        return successful;
    }

    private boolean checkTransactionLookup(List<Transaction> expectedTransactions, byte[] identifier,
                                           String senderLabel) {

        // The lookup returns transactions ordered on timestamp descending.
        List<Transaction> sortedExpectedTransactions = new ArrayList<>(expectedTransactions);
        sortedExpectedTransactions.sort(new Comparator<Transaction>() {
            @Override
            public int compare(Transaction transaction1, Transaction transaction2) {
                return Long.compare(transaction2.getTimestamp(), transaction1.getTimestamp());
            }
        });

        // Get the transactions for the account.
        List<Transaction> retrievedTransactions = TransactionIndexer.transactionsForAccount(identifier);

        // This is a small set of transactions, so the size should match.
        boolean successful = true;
        if (retrievedTransactions.size() != sortedExpectedTransactions.size()) {
            successful = false;
            failureCause = "expected " + sortedExpectedTransactions.size() + " transactions on lookup, got " +
                    retrievedTransactions.size();
        }

        // Check all properties of the transactions.
        for (int i = 0; i < sortedExpectedTransactions.size() && successful; i++) {
            Transaction retrievedTransaction = retrievedTransactions.get(i);
            Transaction expectedTransaction = sortedExpectedTransactions.get(i);

            // Check the type.
            if (retrievedTransaction.getType() != expectedTransaction.getType()) {
                successful = false;
                failureCause = "in checkTransactionLookup(), incorrect type, transaction " + i + " for " +
                        senderLabel + ", expected=" + expectedTransaction.getType() + ", actual=" +
                        retrievedTransaction.getType();
            }

            // Check the timestamp.
            if (successful && retrievedTransaction.getTimestamp() != expectedTransaction.getTimestamp()) {
                successful = false;
                failureCause = "in checkTransactionLookup(), incorrect timestamp, transaction " + i + " for " +
                        senderLabel + ", expected=" + expectedTransaction.getTimestamp() + ", actual=" +
                        retrievedTransaction.getTimestamp();
            }

            // Check the amount.
            if (successful && retrievedTransaction.getAmount() != expectedTransaction.getAmount()) {
                successful = false;
                failureCause = "in checkTransactionLookup(), incorrect amount, transaction " + i + " for " +
                        senderLabel + ", expected=" + PrintUtil.printAmountWithCommas(expectedTransaction.getAmount()) +
                        ", actual=" + PrintUtil.printAmountWithCommas(retrievedTransaction.getAmount());
            }

            // Check the sender identifier.
            if (successful && !ByteUtil.arraysAreEqual(retrievedTransaction.getSenderIdentifier(),
                    expectedTransaction.getSenderIdentifier())) {
                successful = false;
                failureCause = "in checkTransactionLookup(), incorrect sender, transaction " + i + " for " +
                        senderLabel + ", expected=" +
                        ByteUtil.arrayAsStringWithDashes(expectedTransaction.getSenderIdentifier()) + ", actual=" +
                        ByteUtil.arrayAsStringWithDashes(retrievedTransaction.getSenderIdentifier());
            }

            // Check the receiver identifier.
            if (successful && !ByteUtil.arraysAreEqual(retrievedTransaction.getReceiverIdentifier(),
                    expectedTransaction.getReceiverIdentifier())) {
                successful = false;
                failureCause = "in checkTransactionLookup(), incorrect receiver, transaction " + i + " for " +
                        senderLabel + ", expected=" +
                        ByteUtil.arrayAsStringWithDashes(expectedTransaction.getReceiverIdentifier()) + ", actual=" +
                        ByteUtil.arrayAsStringWithDashes(retrievedTransaction.getReceiverIdentifier());
            }

            // Check the previous-hash height.
            if (successful && retrievedTransaction.getPreviousHashHeight() !=
                    expectedTransaction.getPreviousHashHeight()) {
                successful = false;
                failureCause = "in checkTransactionLookup(), incorrect previous-hash height, transaction " + i +
                        " for " + senderLabel + ", expected=" + expectedTransaction.getPreviousHashHeight() +
                        ", actual=" + retrievedTransaction.getPreviousHashHeight();
            }

            // Check the previous-block hash of the retrieved transaction. It should be all zeros, because the lookup
            // process avoids loading the block for efficiency.
            if (successful && !ByteUtil.isAllZeros(retrievedTransaction.getPreviousBlockHash())) {
                successful = false;
                failureCause = "in checkTransactionLookup(), previous block hash of retrieved transaction should be " +
                        "all zeros, transaction " + i + " for " + senderLabel + ", value=" +
                        ByteUtil.arrayAsStringWithDashes(retrievedTransaction.getPreviousBlockHash());
            }

            // Check the previous-block hash of the expected transaction. It *should not* be all zeros.
            if (successful && ByteUtil.isAllZeros(expectedTransaction.getPreviousBlockHash())) {
                successful = false;
                failureCause = "in checkTransactionLookup(), previous block hash of expected transaction should not " +
                        "be all zeros, transaction " + i + " for " + senderLabel + ", value=" +
                        ByteUtil.arrayAsStringWithDashes(expectedTransaction.getPreviousBlockHash());
            }

            // Check the sender data.
            if (successful && !ByteUtil.arraysAreEqual(retrievedTransaction.getSenderData(),
                    expectedTransaction.getSenderData())) {
                successful = false;
                failureCause = "in checkTransactionLookup(), incorrect sender data, transaction " + i + " for " +
                        senderLabel + ", expected=" +
                        ByteUtil.arrayAsStringWithDashes(expectedTransaction.getSenderData()) + ", actual=" +
                        ByteUtil.arrayAsStringWithDashes(retrievedTransaction.getSenderData());
            }

            // Check the signature.
            if (successful && !ByteUtil.arraysAreEqual(retrievedTransaction.getSignature(),
                    expectedTransaction.getSignature())) {
                successful = false;
                failureCause = "in checkTransactionLookup(), incorrect signature, transaction " + i + " for " +
                        senderLabel + ", expected=" +
                        ByteUtil.arrayAsStringWithDashes(expectedTransaction.getSignature()) + ", actual=" +
                        ByteUtil.arrayAsStringWithDashes(retrievedTransaction.getSignature());
            }
        }

        return successful;
    }

    public String getFailureCause() {
        return failureCause;
    }
}
