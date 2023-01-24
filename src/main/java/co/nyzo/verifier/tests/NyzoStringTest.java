package co.nyzo.verifier.tests;

import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.RunMode;
import co.nyzo.verifier.Transaction;
import co.nyzo.verifier.nyzoString.*;
import co.nyzo.verifier.util.PrintUtil;

import java.util.ArrayList;
import java.util.List;
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

        String[] rawSeeds = {
                "74d84ed425f51e6f-aa9bae140e952601-29d16a73241231dc-6962619b5fbc6e27",
                "083a351b43b5b283-adab877df813bf18-0fffce2c72a47482-82ad5f5eeac04bbf",
                "b8339a33324ab024-97954384b6ea9993-57e8b5132369a962-41f74915032b5673",
                "c571973ddbf47ed2-c595706b8ac4f557-4207fe88f823f007-d60e33ec54d5bf28",
                "97dae745780f9879-5c2983ed27f8fae3-7b0efe3247737348-76cc30b53d09cff5",
                "372f5fc01964a907-c2e22b5437023240-8c11069a790ec948-dbadea54512132c1",
                "d43b855022d33bf9-234ee3ffd5cf5e0b-663dade78d54ccd9-c182af72d4fb34ba",
                "3f98368bcb4918ae-ede55d4a14d98a79-dd20eeff942acc68-6a29e40f443d4216",
                "6ef86c169f08e51f-5417da92760b48cf-2a855349332a261b-1b5bdd92cd625ae3",
                "fc0c914efd39b5cb-cf567a6f75847639-67d6b71f2420a9f7-9d887681d3a46a4a",
                "a7f3f7629ad8843c-99441d73fd1b17e1-161c591bb5da5831-bdc6f79f6ca9ad4f",
                "d1376eadfde5daa9-31e165b3f0bef4c1-9b81430c47486dc0-ccca1c69e30680fa",
                "0163f2e202f10b8b-dad1e53ea1992e1a-bd6e54c8d0e5a677-1ff74edc379652d1",
                "5f1d019e58ecd6d4-1cb59bff5912ed3f-293058b05a7d52ad-9077646f9995a4c1",
                "3bde14ad035556d4-4187371ad693cb49-ef2f041f82349660-fb6d7689f29a2fdf"
        };
        String[] nyzoStrings = {
                "key_87jpjKgC.hXMHGLL50Ym9x4GSnGR918PV6CzpqKwM6WEgqRzfABZ",
                "key_80xYdhK3Ksa3IrL7wwxjMPxf_-WJtHhSxFaKoTZHN4L_7md3ZIZw",
                "key_8bxRDAcQiI0BCXm3ybsHDqdoYbkj8UDGpB7Vihk3aTqRRc19vgou",
                "key_8cmPCRVs.7ZiPqmNrWI4.mu21_Y8~2fN1.pec~PkTs-F1z~UFs3n",
                "key_89wrXSmW3XyXo2D3ZiwW~LdZ3MWQhVdRi7sccbk.2t_TNZe8NV6-",
                "key_83tMo-0qqaB7NL8Im3t2cB2c4grrvgZ9idLKYChh8jb1aHbuTfIs",
                "key_8dgZym0zSRMX8SZA_.ofoxKDfrVEAmjcUt62IVbk~RiY51mPR6kc",
                "key_83~pdFMbihzLZvmuiyjqzEEu8eZ_C2Icr6FGX0.4fk8nD6NvR.nE",
                "key_86ZWs1rw2ekwm1wrBEpbic-Hymd9cQFD6PKsVqbdpCIAi3q3iLQw",
                "key_8fNcBkZ.esobRTqYsVn4uACETItw922G.XU8uF7jG6GaUQxNGNI1",
                "key_8awR.UarU8g-Dkgut_Ss5~4n75BsKuGpcsV6.X.JHrTfMA8TdX8F",
                "key_8d4VsHV.XuHGcv5CJ_2~.c6sxkcchSyKNcRa76EA1F3YDgsruj2K",
                "key_805A-L82-gLbUK7CfH6qbyH.sCj8SenDuP_VjKNVCCbh7Y763Hmm",
                "key_85-u0qXpZdsk7bns_TBiZj-Gc5zNnETiIq1Vq6~qCrj1y31e7JvM",
                "key_83Mv5aS3mmskgptV6KrjQSEMbNgwxAinpfKKuFEQDz_w5mc5yPI0"
        };

        // Check decoding and encoding for all values.
        boolean successful = true;
        for (int i = 0; i < rawSeeds.length && successful; i++) {

            byte[] rawSeed = ByteUtil.byteArrayFromHexString(rawSeeds[i], FieldByteSize.seed);
            String nyzoString = nyzoStrings[i];

            // Check decoding against the expected raw seed.
            NyzoStringPrivateSeed decodedKey = (NyzoStringPrivateSeed) NyzoStringEncoder.decode(nyzoString);
            if (decodedKey == null) {
                successful = false;
                failureCause = "unable to decode Nyzo string (" + nyzoString + ") in iteration " + i +
                        " of NyzoStringTest.testPrivateSeedStrings()";
            } else if (!ByteUtil.arraysAreEqual(decodedKey.getSeed(), rawSeed)) {
                successful = false;
                failureCause = "mismatch of expected raw seed (" + ByteUtil.arrayAsStringWithDashes(rawSeed) +
                        ") and decoded seed (" + ByteUtil.arrayAsStringWithDashes(decodedKey.getSeed()) +
                        ") in iteration " + i + " of NyzoStringTest.testPrivateSeedStrings()";
            }

            // Check encoding against the expected encoded string.
            String encodedString = NyzoStringEncoder.encode(new NyzoStringPrivateSeed(rawSeed));
            if (!nyzoString.equals(encodedString)) {
                successful = false;
                failureCause = "mismatch of expected Nyzo string (" + nyzoString + ") and encoded Nyzo string (" +
                        encodedString + ") in iteration " + i + " of NyzoStringTest.testPrivateSeedStrings()";
            }
        }

        System.out.println(TestUtil.passFail(successful));

        return successful;
    }

    private boolean testPublicIdentifierStrings() {

        String[] rawIdentifiers = {
                "c34a6f1942cb7ec1-0d2a440b3e116041-d05df2746ebe7b41-802340a1495e7af5",  // Argo 746
                "b5fd3e8d789a5055-091e46db881f1b74-1b0ab6f8d65b21ae-88cc543dfd92173b",  // Nyzo 0
                "15fa0cd9b1619538-58d097090621a4de-24063449b66d4dac-2af90543389a9f89",  // Nyzo 1
                "4ddefde6a0c5abf7-8868f2c13803f934-c45a0f675f69fd75-0d6578046617eec5",  // Nyzo 2
                "1459eed3a8d3bbf1-d1faaf553f086033-6615d10e0ebd44b5-f8b5c94418457a43",  // Nyzo 3
                "684d8b1bfedb0bb3-19954ba3e330d825-77ed7ffa45fdf468-cfe01accac6db89d",  // Nyzo 4
                "e917bf3cf77b2c8e-100a3715500397ab-cb89f99963a174e1-3c5b4e11cbb72852",  // Nyzo 5
                "f83cf2e0e3abc5df-01bddd67fead3099-bff7efaf467010f5-3b654f293a9a9887",  // Nyzo 6
                "363a10a67dfac9a2-ac59dc7a1fd03e37-05665f01560d399c-78a367dc21ce94ce",  // Nyzo 7
                "de2fd26165e1b774-ce8da5365040fc60-be84a2167e1d62e7-f847b40fea05863a",  // Nyzo 8
                "92a5849feebbb2e1-fb64b6d93940bad3-6168ff0d4f984f1a-7b64dc6fedc0dae5"   // Nyzo 9
        };
        String[] nyzoStrings = {
                "id__8cdasPC2QVZ13iG42RWhp47gow9SsIXZgp0Aga59oEITG2X-M7Ur",  // Argo 746
                "id__8bo.fFTWDC1m2hX6UWxw6Vgs2IsWTCJyIFAcm3V.BytZgoahsDN5",  // Nyzo 0
                "id__81oY3dDPpqkWnd2o2gpyGdWB1Ah9KDTdI2IX1kcWDG~9zSnx2qrV",  // Nyzo 1
                "id__84Vv_vrxPrMVz6AQNjx3~jj4nx.EoUE.ugTCv0hD5~Z5p5hM3PFu",  // Nyzo 2
                "id__81hqZKeFSZMPSwHMmj-8p3dD5u4e3IT4KwzTQkgphoG3ZTTQRQpV",  // Nyzo 3
                "id__86ydzPM~UNLR6qmbF~cNU2mVZo_YhwVSrc_x6JQJsszua_S7524c",  // Nyzo 4
                "id__8eBoMRRVvQQe40FV5m03CYMbzwDqpY5SWjPsjy7bKQyi07~ubwHD",  // Nyzo 5
                "id__8fx--L3AH-ow0sVuq_YKc9D_.~~MhE0g.jKCjQBYDGz72811JoPW",  // Nyzo 6
                "id__83pY4aq.~JDzI5Etvy_gfAt5qC-1mxSXE7zAq.NyRGjeRKZ72xT5",  // Nyzo 7
                "id__8dWMSD5CWsuSRFUCdC10_62~ya8nwyTzX_y7K0_H1ppYBEsF1teA",  // Nyzo 8
                "id__89aCy9_LLZby~UiUUjC0LKdyrf-djXyf6EKBV6_KNdICIpCfGqK3"   // Nyzo 9
        };

        // Check decoding and encoding for all values.
        boolean successful = true;
        for (int i = 0; i < rawIdentifiers.length && successful; i++) {

            byte[] rawIdentifier = ByteUtil.byteArrayFromHexString(rawIdentifiers[i], FieldByteSize.identifier);
            String nyzoString = nyzoStrings[i];

            // Check decoding against the expected raw identifier.
            NyzoStringPublicIdentifier decodedIdentifier =
                    (NyzoStringPublicIdentifier) NyzoStringEncoder.decode(nyzoString);
            if (decodedIdentifier == null) {
                successful = false;
                failureCause = "unable to decode Nyzo string (" + nyzoString + ") in iteration " + i +
                        " of NyzoStringTest.testPublicIdentifierStrings()";
            } else if (!ByteUtil.arraysAreEqual(decodedIdentifier.getIdentifier(), rawIdentifier)) {
                successful = false;
                failureCause = "mismatch of expected raw identifier (" +
                        ByteUtil.arrayAsStringWithDashes(rawIdentifier) + ") and decoded identifier (" +
                        ByteUtil.arrayAsStringWithDashes(decodedIdentifier.getIdentifier()) + ") in iteration " + i +
                        " of NyzoStringTest.testPublicIdentifierStrings()";
            }

            // Check encoding against the expected encoded string.
            String encodedString = NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(rawIdentifier));
            if (!nyzoString.equals(encodedString)) {
                successful = false;
                failureCause = "mismatch of expected Nyzo string (" + nyzoString + ") and encoded Nyzo string (" +
                        encodedString + ") in iteration " + i + " of NyzoStringTest.testPublicIdentifierStrings()";
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
