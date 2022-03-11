package co.nyzo.verifier.client;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.*;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class TransactionIndexer {

    private static final String activeKey = "transaction_indexer_active";
    private static final boolean indexingActive = PreferencesUtil.getBoolean(activeKey, true);

    private static final File directory = new File(Verifier.dataRootDirectory, "indexed_transactions");
    private static final int indexEntrySize = FieldByteSize.timestamp + FieldByteSize.transactionAmount +
            13;  // 13 == 8 bytes of sender data + 1 byte for sender/receiver + 4 bytes for transaction offset in file
    public static final int maximumTransactionsPerQuery = 100;

    public static void indexTransactionsForBlock(Block block) {
        if (block != null) {
            // Ensure the directory exists.
            directory.mkdirs();

            // Write all transactions to the index files for both sender and receiver.
            for (Transaction transaction : block.getTransactions()) {
                if (transaction.getType() == Transaction.typeStandard) {
                    // Write the transaction to the sender file.
                    int fileOffsetSender = writeTransactionToList(transaction,
                            listFileForAccount(transaction.getSenderIdentifier()));
                    if (fileOffsetSender >= 0) {
                        writeTransactionToIndex(transaction, indexFileForAccount(transaction.getSenderIdentifier()),
                                true, fileOffsetSender);
                    }

                    // Write the transaction to the receiver file.
                    int fileOffsetReceiver = writeTransactionToList(transaction,
                            listFileForAccount(transaction.getReceiverIdentifier()));
                    if (fileOffsetReceiver >= 0) {
                        writeTransactionToIndex(transaction, indexFileForAccount(transaction.getReceiverIdentifier()),
                                false, fileOffsetReceiver);
                    }
                }
            }
        }
    }

    public static List<Transaction> transactionsForAccount(byte[] accountIdentifier) {

        List<Transaction> transactions = new ArrayList<>();
        RandomAccessFile indexFileReader = null;
        RandomAccessFile listFileReader = null;
        try {
            // Get the list of timestamps from the index file, starting at the end of the file. This will return the
            // most recent transactions first.
            List<Integer> offsets = new ArrayList<>();
            indexFileReader = new RandomAccessFile(indexFileForAccount(accountIdentifier), "r");
            long filePosition = indexFileReader.length() - indexEntrySize;
            while (filePosition >= 0 && offsets.size() < maximumTransactionsPerQuery) {
                // Seek and read the index information.
                indexFileReader.seek(filePosition);
                long timestamp = indexFileReader.readLong();
                long amount = indexFileReader.readLong();
                byte[] senderData = Message.getByteArray(indexFileReader, 8);
                boolean isSender = indexFileReader.readBoolean();
                int offset = indexFileReader.readInt();

                // Store the offset.
                offsets.add(offset);

                // Calculate the new file position, stepping back one record.
                filePosition = indexFileReader.getFilePointer() - indexEntrySize * 2;
            }
            indexFileReader.close();

            // Read the transactions from the list file.
            if (!offsets.isEmpty()) {
                listFileReader = new RandomAccessFile(listFileForAccount(accountIdentifier), "r");
                for (int offset : offsets) {
                    listFileReader.seek(offset);
                    byte type = listFileReader.readByte();
                    long timestamp = listFileReader.readLong();
                    long amount = listFileReader.readLong();
                    byte[] receiverIdentifier = Message.getByteArray(listFileReader, FieldByteSize.identifier);
                    long previousHashHeight = listFileReader.readLong();
                    byte[] previousBlockHash = new byte[FieldByteSize.hash];
                    byte[] senderIdentifier = Message.getByteArray(listFileReader, FieldByteSize.identifier);
                    int senderDataLength = Math.min(listFileReader.readByte(), 32);
                    byte[] senderData = Message.getByteArray(listFileReader, senderDataLength);
                    byte[] signature = Message.getByteArray(listFileReader, FieldByteSize.signature);

                    transactions.add(Transaction.standardTransaction(timestamp, amount, receiverIdentifier,
                            previousHashHeight, previousBlockHash, senderIdentifier, senderData, signature));
                }
            }
        } catch (Exception e) {
            LogUtil.println(ConsoleColor.Red.backgroundBright() + "exception getting transactions for account: " +
                    ByteUtil.arrayAsStringWithDashes(accountIdentifier) + ": " + PrintUtil.printException(e) +
                    ConsoleColor.reset);
        }

        // Close both files.
        safeClose(indexFileReader);
        safeClose(listFileReader);

        return transactions;
    }

    private static int writeTransactionToList(Transaction transaction, File file) {

        int offset;
        RandomAccessFile fileWriter = null;
        try {
            fileWriter = new RandomAccessFile(file, "rw");
            offset = (int) fileWriter.length();
            fileWriter.seek(fileWriter.length());
            fileWriter.write(transaction.getBytes());
        } catch (Exception e) {
            offset = -1;
        }

        // Close the file.
        safeClose(fileWriter);

        return offset;
    }

    private static void writeTransactionToIndex(Transaction transaction, File file, boolean isSender, int fileOffset) {

        // If the index file exists, rewrite it with the new entry.
        if (file.exists()) {
            RandomAccessFile fileReader = null;
            RandomAccessFile fileWriter = null;
            File temporaryFile = new File(file.getAbsolutePath() + "_temp");
            try {
                // Ensure the temporary file does not already exist.
                if (!temporaryFile.exists() || temporaryFile.delete()) {
                    // Open the previous file for reading and the temporary location for writing.
                    fileReader = new RandomAccessFile(file, "r");
                    fileWriter = new RandomAccessFile(temporaryFile, "rw");

                    // Rewrite the file, inserting the new entry in the appropriate location so the indices are ordered
                    // on ascending timestamp. This is written so blocks can be indexed in any order while retaining
                    // timestamp order.
                    long newTransactionTimestamp = transaction.getTimestamp();
                    byte[] fileEntryArray = new byte[indexEntrySize];
                    ByteBuffer fileEntryBuffer = ByteBuffer.wrap(fileEntryArray);
                    boolean wroteEntry = false;
                    while (fileReader.read(fileEntryArray) == indexEntrySize) {
                        // Write the transaction as soon as the timestamp of the existing entry is greater than or equal
                        // to the transaction timestamp.
                        if (!wroteEntry) {
                            fileEntryBuffer.position(0);
                            long fileEntryTimestamp = fileEntryBuffer.getLong();
                            if (fileEntryTimestamp >= newTransactionTimestamp) {
                                // Write the new transaction if it is not already contained in the file.
                                byte[] newTransactionArray = transactionIndexEntry(transaction, isSender, fileOffset);
                                if (!ByteUtil.arraysAreEqual(newTransactionArray, fileEntryArray)) {
                                    fileWriter.write(newTransactionArray);
                                }

                                // Mark that the entry was written so it will not be written later.
                                wroteEntry = true;
                            }
                        }

                        // Write the entry from the existing file into the new file.
                        fileWriter.write(fileEntryArray);
                    }

                    // If the entry has not yet been written, write it at the end of the file.
                    if (!wroteEntry) {
                        fileWriter.write(transactionIndexEntry(transaction, isSender, fileOffset));
                    }
                }
            } catch (Exception e) {
                LogUtil.println(ConsoleColor.Red.backgroundBright() + "exception writing transaction to index file: " +
                        PrintUtil.printException(e) + ConsoleColor.reset);
            }

            // Close the files.
            safeClose(fileReader);
            safeClose(fileWriter);

            // Move the temporary file to replace the old file.
            try {
                Path temporaryPath = Paths.get(temporaryFile.getAbsolutePath());
                Path path = Paths.get(file.getAbsolutePath());
                Files.move(temporaryPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                LogUtil.println(ConsoleColor.Red.backgroundBright() + "exception moving index file to permanent " +
                        "location: " + PrintUtil.printException(e) + ConsoleColor.reset);
            }

        } else {
            try {
                // Otherwise, write the transaction directly to the file as the first entry.
                Files.write(Paths.get(file.getAbsolutePath()), transactionIndexEntry(transaction, isSender,
                        fileOffset));
            } catch (Exception e) {
                LogUtil.println(ConsoleColor.Red.backgroundBright() + "exception writing new transaction index file: " +
                        PrintUtil.printException(e) + ConsoleColor.reset);
            }
        }
    }

    private static File listFileForAccount(byte[] accountIdentifier) {
        return new File(directory, ByteUtil.arrayAsStringWithDashes(accountIdentifier) + ".nyzotransactionlist");
    }

    private static File indexFileForAccount(byte[] accountIdentifier) {
        return new File(directory, ByteUtil.arrayAsStringWithDashes(accountIdentifier) + ".nyzotransactionindex");
    }

    private static byte[] transactionIndexEntry(Transaction transaction, boolean isSender, int fileOffset) {

        // The entries are 29 bytes long: 8 bytes for timestamp, 8 bytes for amount, the first 8 bytes of the sender
        // data, a byte for sender/receiver, and 4 bytes for file offset.
        byte[] result = new byte[indexEntrySize];

        ByteBuffer byteBuffer = ByteBuffer.wrap(result);
        byteBuffer.putLong(transaction.getTimestamp());
        byteBuffer.putLong(transaction.getAmount());
        if (transaction.getSenderData() != null) {
            int lengthToCopy = Math.min(8, transaction.getSenderData().length);
            System.arraycopy(transaction.getSenderData(), 0, result, 16, lengthToCopy);
        }
        byteBuffer.position(byteBuffer.position() + 8);
        byteBuffer.put(isSender ? (byte) 1 : (byte) 0);
        byteBuffer.putInt(fileOffset);

        return result;
    }

    private static void safeClose(RandomAccessFile file) {
        if (file != null) {
            try {
                file.close();
            } catch (Exception ignored) { }
        }
    }
}
