package co.nyzo.verifier.tests;

import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.Transaction;
import co.nyzo.verifier.nyzoString.*;
import co.nyzo.verifier.util.PrintUtil;

import java.util.Random;

public class NyzoStringTest implements NyzoTest {

    private String failureCause = null;

    public static void main(String[] args) {

        NyzoStringTest test = new NyzoStringTest();
        boolean successful = test.run();

        if (!successful) {
            System.out.println(TestUtil.failureCause(test.getFailureCause()));
        }
    }

    public boolean run() {

        boolean successful;
        try {
            successful = testPrivateSeedStrings();
        } catch (Exception e) {
            failureCause = "exception in NyzoStringTest.testPrivateSeedStrings(): " + PrintUtil.printException(e);
            successful = false;
        }

        if (successful) {
            try {
                successful = testPublicIdentifierStrings();
            } catch (Exception e) {
                failureCause = "exception in NyzoStringTest.testPublicIdentifierStrings(): " +
                        PrintUtil.printException(e);
                successful = false;
            }
        }

        if (successful) {
            try {
                successful = testNyzoMicropayStrings();
            } catch (Exception e) {
                failureCause = "exception in NyzoStringTest.testNyzoMicropayStrings(): " + PrintUtil.printException(e);
                successful = false;
            }
        }

        if (successful) {
            try {
                successful = testPrefilledDataStrings();
            } catch (Exception e) {
                failureCause = "exception in NyzoStringTest.testPrefilledDataStrings(): " + PrintUtil.printException(e);
                successful = false;
            }
        }

        if (successful) {
            try {
                successful = testTransactionStrings();
            } catch (Exception e) {
                failureCause = "exception in NyzoStringTest.testTransactionStrings(): " + PrintUtil.printException(e);
                successful = false;
            }
        }

        return successful;
    }

    public String getFailureCause() {
        return failureCause;
    }

    private boolean testPrivateSeedStrings() {

        // Create a pseudo-random generator. Using a fixed seed ensures reproducibility of problems.
        Random random = new Random(3290);

        // Test 10000 private-seed strings.
        boolean successful = true;
        for (int i = 0; i < 100000 && successful; i++) {

            // Make and encode the string from random bytes.
            byte[] seed = randomArray(random, FieldByteSize.seed);
            NyzoStringPrivateSeed stringObject = new NyzoStringPrivateSeed(seed);
            String encoded = NyzoStringEncoder.encode(stringObject);

            // Reverse the process to get the bytes.
            NyzoStringPrivateSeed decodedString = (NyzoStringPrivateSeed) NyzoStringEncoder.decode(encoded);
            byte[] decodedSeed = decodedString.getSeed();
            if (!ByteUtil.arraysAreEqual(seed, decodedSeed)) {
                successful = false;
                failureCause = "mismatch of generated seed (" + ByteUtil.arrayAsStringWithDashes(seed) +
                        ") and decoded seed (" + ByteUtil.arrayAsStringWithDashes(decodedSeed) +
                        ") in iteration " + i + " of NyzoStringTest.testPrivateSeedStrings()";
            }
        }

        System.out.println(TestUtil.passFail(successful));

        return successful;
    }

    private boolean testPublicIdentifierStrings() {

        // Create a pseudo-random generator. Using a fixed seed ensures reproducibility of problems.
        Random random = new Random(9868);

        // Test 10000 public-identifier strings.
        boolean successful = true;
        for (int i = 0; i < 100000 && successful; i++) {

            // Make and encode the string from random bytes.
            byte[] identifier = randomArray(random, FieldByteSize.identifier);
            NyzoStringPublicIdentifier stringObject = new NyzoStringPublicIdentifier(identifier);
            String encoded = NyzoStringEncoder.encode(stringObject);

            // Reverse the process to get the bytes.
            NyzoStringPublicIdentifier decodedString = (NyzoStringPublicIdentifier) NyzoStringEncoder.decode(encoded);
            byte[] decodedIdentifier = decodedString.getIdentifier();
            if (!ByteUtil.arraysAreEqual(identifier, decodedIdentifier)) {
                successful = false;
                failureCause = "mismatch of generated identifier (" + ByteUtil.arrayAsStringWithDashes(identifier) +
                        ") and decoded identifier (" + ByteUtil.arrayAsStringWithDashes(decodedIdentifier) +
                        ") in iteration " + i + " of NyzoStringTest.testPublicIdentifierStrings()";
            }
        }

        System.out.println(TestUtil.passFail(successful));

        return successful;
    }

    private boolean testNyzoMicropayStrings() {

        // Create a pseudo-random generator. Using a fixed seed ensures reproducibility of problems.
        Random random = new Random(8059);

        // Test 10000 strings.
        boolean successful = true;
        for (int i = 0; i < 100000 && successful; i++) {

            // Make and encode the string from random bytes. For amount, force some 0 values.
            byte[] receiverIdentifier = randomArray(random, FieldByteSize.identifier);
            int senderDataLength = random.nextInt(FieldByteSize.maximumSenderDataLength + 1);
            byte[] senderData = randomArray(random, senderDataLength);
            long amount = random.nextInt(5) == 0 ? 0 : random.nextLong();
            long timestamp = random.nextLong();
            long previousHashHeight = random.nextLong();
            byte[] previousBlockHash = randomArray(random, FieldByteSize.hash);

            NyzoStringMicropay stringObject = new NyzoStringMicropay(receiverIdentifier, senderData, amount, timestamp,
                    previousHashHeight, previousBlockHash);
            String encoded = NyzoStringEncoder.encode(stringObject);

            // Reverse the process to get the object.
            NyzoStringMicropay decodedString = (NyzoStringMicropay) NyzoStringEncoder.decode(encoded);

            if (!ByteUtil.arraysAreEqual(receiverIdentifier, decodedString.getReceiverIdentifier())) {
                successful = false;
                failureCause = "mismatch of generated receiver identifier (" +
                        ByteUtil.arrayAsStringWithDashes(receiverIdentifier) + ") and decoded receiver identifier (" +
                        ByteUtil.arrayAsStringWithDashes(decodedString.getReceiverIdentifier()) +
                        ") in iteration " + i + " of NyzoStringTest.testNyzoMicropayStrings()";
            }

            if (!ByteUtil.arraysAreEqual(senderData, decodedString.getSenderData())) {
                successful = false;
                failureCause = "mismatch of generated (" + ByteUtil.arrayAsStringWithDashes(senderData) +
                        ") and decoded sender data (" +
                        ByteUtil.arrayAsStringWithDashes(decodedString.getSenderData()) + ") in iteration " + i +
                        " of NyzoStringTest.testNyzoMicropayStrings()";
            }

            if (amount != decodedString.getAmount()) {
                successful = false;
                failureCause = "mismatch of generated amount (" + PrintUtil.printAmount(amount) +
                        ") and decoded amount (" + PrintUtil.printAmount(decodedString.getAmount()) +
                        ") in iteration " + i + " of NyzoStringTest.testNyzoMicropayStrings()";
            }

            if (timestamp != decodedString.getTimestamp()) {
                successful = false;
                failureCause = "mismatch of generated (" + timestamp + ") and decoded timestamp (" +
                        decodedString.getTimestamp() + ") in iteration " + i +
                        " of NyzoStringTest.testNyzoMicropayStrings()";
            }

            if (previousHashHeight != decodedString.getPreviousHashHeight()) {
                successful = false;
                failureCause = "mismatch of generated (" + previousHashHeight + ") and decoded previous-hash height (" +
                        decodedString.getPreviousHashHeight() + ") in iteration " + i +
                        " of NyzoStringTest.testNyzoMicropayStrings()";
            }

            if (!ByteUtil.arraysAreEqual(previousBlockHash, decodedString.getPreviousBlockHash())) {
                successful = false;
                failureCause = "mismatch of generated (" + ByteUtil.arrayAsStringWithDashes(previousBlockHash) +
                        ") and decoded previous-block hash (" +
                        ByteUtil.arrayAsStringWithDashes(decodedString.getPreviousBlockHash()) + ") in iteration " + i +
                        " of NyzoStringTest.testNyzoMicropayStrings()";
            }
        }

        System.out.println(TestUtil.passFail(successful));

        return successful;
    }

    private boolean testPrefilledDataStrings() {

        // Create a pseudo-random generator. Using a fixed seed ensures reproducibility of problems.
        Random random = new Random(9199);

        // Test 10000 strings.
        boolean successful = true;
        for (int i = 0; i < 100000 && successful; i++) {

            // Make and encode the string from random bytes.
            byte[] receiverIdentifier = randomArray(random, FieldByteSize.identifier);
            int senderDataLength = random.nextInt(FieldByteSize.maximumSenderDataLength + 1);
            byte[] senderData = randomArray(random, senderDataLength);

            NyzoStringPrefilledData stringObject = new NyzoStringPrefilledData(receiverIdentifier, senderData);
            String encoded = NyzoStringEncoder.encode(stringObject);

            // Reverse the process to get the object.
            NyzoStringPrefilledData decodedString = (NyzoStringPrefilledData) NyzoStringEncoder.decode(encoded);

            if (!ByteUtil.arraysAreEqual(receiverIdentifier, decodedString.getReceiverIdentifier())) {
                successful = false;
                failureCause = "mismatch of generated receiver identifier (" +
                        ByteUtil.arrayAsStringWithDashes(receiverIdentifier) + ") and decoded receiver identifier (" +
                        ByteUtil.arrayAsStringWithDashes(decodedString.getReceiverIdentifier()) +
                        ") in iteration " + i + " of NyzoStringTest.testPrefilledDataStrings()";
            }

            if (!ByteUtil.arraysAreEqual(senderData, decodedString.getSenderData())) {
                successful = false;
                failureCause = "mismatch of generated sender data (" + ByteUtil.arrayAsStringWithDashes(senderData) +
                        ") and decoded sender data (" +
                        ByteUtil.arrayAsStringWithDashes(decodedString.getSenderData()) + ") in iteration " + i +
                        " of NyzoStringTest.testPrefilledDataStrings()";
            }
        }

        System.out.println(TestUtil.passFail(successful));

        return successful;
    }

    private boolean testTransactionStrings() {

        // Create a pseudo-random generator. Using a fixed seed ensures reproducibility of problems.
        Random random = new Random(1911);

        // Test 10000 strings.
        boolean successful = true;
        for (int i = 0; i < 100000 && successful; i++) {

            // Make and encode the transaction from random values.
            long timestamp = random.nextLong();
            long amount = random.nextLong();
            byte[] receiverIdentifier = randomArray(random, FieldByteSize.identifier);
            long previousHashHeight = random.nextLong();
            byte[] previousBlockHash = randomArray(random, FieldByteSize.hash);
            byte[] senderIdentifier = randomArray(random, FieldByteSize.identifier);
            int senderDataLength = random.nextInt(FieldByteSize.maximumSenderDataLength + 1);
            byte[] senderData = randomArray(random, senderDataLength);
            byte[] signature = randomArray(random, FieldByteSize.signature);
            Transaction transaction = Transaction.standardTransaction(timestamp, amount, receiverIdentifier,
                    previousHashHeight, previousBlockHash, senderIdentifier, senderData, signature);

            NyzoStringTransaction stringObject = new NyzoStringTransaction(transaction);
            String encoded = NyzoStringEncoder.encode(stringObject);

            // Reverse the process to get the object.
            NyzoStringTransaction decodedString = (NyzoStringTransaction) NyzoStringEncoder.decode(encoded);
            Transaction decodedTransaction = decodedString.getTransaction();

            if (timestamp != decodedTransaction.getTimestamp()) {
                successful = false;
                failureCause = "mismatch of generated (" + timestamp + ") and decoded timestamp (" +
                        decodedTransaction.getTimestamp() + ") in iteration " + i +
                        " of NyzoStringTest.testTransactionStrings()";
            }

            if (amount != decodedTransaction.getAmount()) {
                successful = false;
                failureCause = "mismatch of generated (" + amount + ") and decoded amount (" +
                        decodedTransaction.getAmount() + ") in iteration " + i +
                        " of NyzoStringTest.testTransactionStrings()";
            }

            if (!ByteUtil.arraysAreEqual(receiverIdentifier, decodedTransaction.getReceiverIdentifier())) {
                successful = false;
                failureCause = "mismatch of generated (" + ByteUtil.arrayAsStringWithDashes(receiverIdentifier) +
                        ") and decoded receiver identifier (" +
                        ByteUtil.arrayAsStringWithDashes(decodedTransaction.getReceiverIdentifier()) +
                        ") in iteration " + i + " of NyzoStringTest.testTransactionStrings()";
            }

            if (previousHashHeight != decodedTransaction.getPreviousHashHeight()) {
                successful = false;
                failureCause = "mismatch of generated (" + previousHashHeight + ") and decoded hash height (" +
                        decodedTransaction.getPreviousHashHeight() + ") in iteration " + i +
                        " of NyzoStringTest.testTransactionStrings()";
            }

            // The previous-block hash should always be all 0s, as it is not stored in a transaction.
            if (!ByteUtil.isAllZeros(decodedTransaction.getPreviousBlockHash())) {
                successful = false;
                failureCause = "previous-block hash is " +
                        ByteUtil.arrayAsStringWithDashes(decodedTransaction.getPreviousBlockHash()) +
                        ", should be all 0s, in iteration " + i + " of NyzoStringTest.testTransactionStrings()";
            }

            if (!ByteUtil.arraysAreEqual(senderIdentifier, decodedTransaction.getSenderIdentifier())) {
                successful = false;
                failureCause = "mismatch of generated (" + ByteUtil.arrayAsStringWithDashes(senderIdentifier) +
                        ") and decoded sender identifier " +
                        ByteUtil.arrayAsStringWithDashes(decodedTransaction.getSenderIdentifier()) +
                        ") in iteration " + i + " of NyzoStringTest.testTransactionStrings()";
            }

            if (!ByteUtil.arraysAreEqual(senderData, decodedTransaction.getSenderData())) {
                successful = false;
                failureCause = "mismatch of generated (" + ByteUtil.arrayAsStringWithDashes(senderData) +
                        ") and decoded sender data " +
                        ByteUtil.arrayAsStringWithDashes(decodedTransaction.getSenderData()) +
                        ") in iteration " + i + " of NyzoStringTest.testTransactionStrings()";
            }

            if (!ByteUtil.arraysAreEqual(signature, decodedTransaction.getSignature())) {
                successful = false;
                failureCause = "mismatch of generated (" + ByteUtil.arrayAsStringWithDashes(signature) +
                        ") and decoded signature " +
                        ByteUtil.arrayAsStringWithDashes(decodedTransaction.getSignature()) +
                        ") in iteration " + i + " of NyzoStringTest.testTransactionStrings()";
            }
        }

        System.out.println(TestUtil.passFail(successful));

        return successful;
    }

    private static byte[] randomArray(Random random, int length) {

        byte[] array = new byte[length];
        random.nextBytes(array);
        return array;
    }
}
