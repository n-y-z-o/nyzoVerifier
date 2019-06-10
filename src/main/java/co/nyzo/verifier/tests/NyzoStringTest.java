package co.nyzo.verifier.tests;

import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.FieldByteSize;
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

            // Make and encode the string from random bytes. For amount and receiver IP, force some 0 values.
            byte[] receiverIdentifier = randomArray(random, FieldByteSize.identifier);
            int senderDataLength = random.nextInt(FieldByteSize.maximumSenderDataLength + 1);
            byte[] senderData = randomArray(random, senderDataLength);
            long amount = random.nextInt(5) == 0 ? 0 : random.nextLong();
            byte[] receiverIpAddress = random.nextInt(5) == 0 ? new byte[FieldByteSize.ipAddress] :
                    randomArray(random, FieldByteSize.ipAddress);
            int receiverPort = random.nextInt();

            NyzoStringMicropay stringObject = new NyzoStringMicropay(receiverIdentifier, senderData, amount,
                    receiverIpAddress, receiverPort);
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
                failureCause = "mismatch of generated sender data (" + ByteUtil.arrayAsStringWithDashes(senderData) +
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

            if (!ByteUtil.arraysAreEqual(receiverIpAddress, decodedString.getReceiverIpAddress())) {
                successful = false;
                failureCause = "mismatch of generated IP address (" +
                        ByteUtil.arrayAsStringWithDashes(receiverIpAddress) + ") and decoded IP address (" +
                        ByteUtil.arrayAsStringWithDashes(decodedString.getReceiverIpAddress()) + ") in iteration " + i +
                        " of NyzoStringTest.testNyzoMicropayStrings()";
            }

            if (receiverPort != decodedString.getReceiverPort()) {
                successful = false;
                failureCause = "mismatch of generated port (" + receiverPort + ") and decoded port (" +
                        decodedString.getReceiverPort() + ") in iteration " + i +
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

    private static byte[] randomArray(Random random, int length) {

        byte[] array = new byte[length];
        random.nextBytes(array);
        return array;
    }
}
