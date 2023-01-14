package co.nyzo.verifier.tests;

import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.RunMode;
import co.nyzo.verifier.Transaction;
import co.nyzo.verifier.nyzoString.*;
import co.nyzo.verifier.util.PrintUtil;

import java.util.Random;

public class NyzoStringTest implements NyzoTest {

    private String failureCause = null;

    public static void main(String[] args) {

        RunMode.setRunMode(RunMode.Test);
        NyzoStringTest test = new NyzoStringTest();
        boolean successful = test.run();

        if (!successful) {
            System.out.println(TestUtil.failureCause(test.getFailureCause()));
        }
    }

    public boolean run() {

        boolean successful;
        try {
            successful = testEncoder();
        } catch (Exception e) {
            failureCause = "exception in NyzoStringTest.testEncoder(): " + PrintUtil.printException(e);
            successful = false;
        }

        if (successful) {
            try {
                successful = testPrivateSeedStrings();
            } catch (Exception e) {
                failureCause = "exception in NyzoStringTest.testPrivateSeedStrings(): " + PrintUtil.printException(e);
                successful = false;
            }
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

    private boolean testEncoder() {

        // The encoding lookup table is 64 characters. 64 characters store 6 bits (2^6=64). This allows the encoder to
        // store 3 bytes (24 bits) in 4 characters. This test checks encoding and decoding up to double this packing
        // increment, from 1 to 6 bytes (1 to 8 characters).

        String[] byteArrays = new String[] { "89", "712aab", "3a0536", "bd53aa", "2e", "98", "9d65ed", "1bdd1542ca",
                "0b49ddab", "78fdccc42427", "994d0200", "0e0000", "40", "e6093ad212", "19941b43", "e5221d048a",
                "b419", "65bb880d55", "6a2b74e7f9", "595c", "db851f52", "7193", "a817", "82", "9f2e4dee75f2",
                "de805a40e3e9", "2920d328b493", "5151", "1e1345be", "826b82f468dd" };
        String[] strings = new String[] {"zg", "tiHI", "exkU", "MmeH", "bx", "D0", "EnoK", "6.SmgJF", "2SEuHN",
                "vfVcP2gE", "DkS200", "3x00", "g0", "XxBYSy8", "6qgsgN", "Xi8u18F", "K1B", "qsL83mk", "rzKSX_B", "nmN",
                "UWkwkx", "tqc", "H1t", "xx", "EQXdZEoQ", "VF1rgefG", "ai3jabij", "km4", "7yd5Mx", "xDL2.6Au" };

        // Check decoding and encoding for all values.
        boolean successful = true;
        for (int i = 0; i < byteArrays.length && successful; i++) {
            byte[] byteArray = ByteUtil.byteArrayFromHexString(byteArrays[i], byteArrays[i].length() / 2);
            String string = strings[i];

            // Check decoding against the expected byte array.
            byte[] decodedByteArray = NyzoStringEncoder.byteArrayForEncodedString(string);
            if (!ByteUtil.arraysAreEqual(byteArray, decodedByteArray)) {
                successful = false;
                failureCause = "mismatch of expected byte array (" + ByteUtil.arrayAsStringNoDashes(byteArray) +
                        ") and decoded byte array (" + ByteUtil.arrayAsStringNoDashes(decodedByteArray) +
                        ") in iteration " + i + " of NyzoStringTest.testEncoder()";
            }

            // Check encoding against the expected encoded string.
            String encodedString = NyzoStringEncoder.encodedStringForByteArray(byteArray);
            if (!string.equals(encodedString)) {
                successful = false;
                failureCause = "mismatch of expected string (" + string + ") and encoded string (" + encodedString +
                        ") in iteration " + i + " of NyzoStringTest.testEncoder()";
            }
        }

        System.out.println(TestUtil.passFail(successful));

        return successful;
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
            long amount = i % 2 == 0 ? 0 : (Math.abs(random.nextLong()) % Transaction.micronyzosInSystem);

            NyzoStringPrefilledData stringObject = new NyzoStringPrefilledData(receiverIdentifier, senderData,  amount);
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

            if (amount != decodedString.getAmount()) {
                successful = false;
                failureCause = "mismatch of generated amount (" + PrintUtil.printAmount(amount) +
                        ") and decoded amount (" + PrintUtil.printAmount(decodedString.getAmount()) +
                        ") in iteration " + i + " of NyzoStringTest.testPrefilledDataStrings()";
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
        for (int i = 0; i < 10000 && successful; i++) {

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
