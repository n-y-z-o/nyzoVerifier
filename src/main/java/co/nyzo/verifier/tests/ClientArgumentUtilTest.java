package co.nyzo.verifier.tests;

import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.client.ClientArgumentUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.charset.StandardCharsets;

public class ClientArgumentUtilTest implements NyzoTest {

    private String failureCause = null;

    public static void main(String[] args) {

        ClientArgumentUtilTest test = new ClientArgumentUtilTest();
        boolean successful = test.run();

        if (!successful) {
            System.out.println(TestUtil.failureCause(test.getFailureCause()));
        }
    }

    public boolean run() {

        boolean successful;
        try {
            // Test the numerical methods.
            successful = testIntegerMethods();
            if (successful) {
                successful = testLongMethods();
            }

            // Test the sender-data method.
            if (successful) {
                successful = testSenderDataMethod();
            }
        } catch (Exception e) {
            successful = false;
            failureCause = "exception in " + getClass().getSimpleName() + ": " + PrintUtil.printException(e);
        }

        System.out.println(TestUtil.passFail(successful));

        return successful;
    }

    private boolean testIntegerMethods() {
        // These values test a variety of cases as noted in the comments. Null values for minimum and maximum indicate
        // use of the non-limiting version of the method.
        boolean successful = true;
        Object[] testValues = {
                "-1", null, null, 0, -1,       // parsable input without limits
                "100", 10, 100, 0, 100,        // parsable input with limits that do not affect result
                "blah", null, null, 100, 100,  // unparsable input without limits
                "20", 30, 40, 0, 30,           // parsable input below lower limit
                "50", 30, 40, 0, 40,           // unparsable input above upper limit
                "Nyzo", 10, 9, 0, 10,          // unparsable input with inconsistent limits
                Integer.MAX_VALUE + "", null, null, 0, Integer.MAX_VALUE,                   // extreme value
                Integer.MIN_VALUE + "", Integer.MIN_VALUE + 1, 0, 0, Integer.MIN_VALUE + 1  // extreme value with limits
        };

        for (int i = 0; i < testValues.length && successful; i += 5) {
            String input = (String) testValues[i];
            Integer minimumValue = (Integer) testValues[i + 1];
            Integer maximumValue = (Integer) testValues[i + 2];
            int defaultValue = (Integer) testValues[i + 3];
            int expectedOutput = (Integer) testValues[i + 4];

            int output;
            if (minimumValue == null) {
                output = ClientArgumentUtil.getInteger(input, defaultValue);
            } else {
                output = ClientArgumentUtil.getInteger(input, defaultValue, minimumValue, maximumValue);
            }

            if (output != expectedOutput) {
                successful = false;
                failureCause = "iteration " + (i / 5) + " of testIntegerMethods(), expected output=" + expectedOutput +
                        ", actual=" + output;
            }
        }

        return successful;
    }

    private boolean testLongMethods() {
        // These values test a variety of cases as noted in the comments. Null values for minimum and maximum indicate
        // use of the non-limiting version of the method.
        boolean successful = true;
        Object[] testValues = {
                "-1", null, null, 0L, -1L,       // parsable input without limits
                "100", 10L, 100L, 0L, 100L,      // parsable input with limits that do not affect result
                "blah", null, null, 100L, 100L,  // unparsable input without limits
                "20", 30L, 40L, 0L, 30L,         // parsable input below lower limit
                "50", 30L, 40L, 0L, 40L,         // unparsable input above upper limit
                "Nyzo", 10L, 9L, 0L, 10L,        // unparsable input with inconsistent limits
                Long.MAX_VALUE + "", null, null, 0L, Long.MAX_VALUE,                 // extreme value
                Long.MIN_VALUE + "", Long.MIN_VALUE + 1, 0L, 0L, Long.MIN_VALUE + 1  // extreme value with limits
        };

        for (int i = 0; i < testValues.length && successful; i += 5) {
            String input = (String) testValues[i];
            Long minimumValue = (Long) testValues[i + 1];
            Long maximumValue = (Long) testValues[i + 2];
            long defaultValue = (Long) testValues[i + 3];
            long expectedOutput = (Long) testValues[i + 4];

            long output;
            if (minimumValue == null) {
                output = ClientArgumentUtil.getLong(input, defaultValue);
            } else {
                output = ClientArgumentUtil.getLong(input, defaultValue, minimumValue, maximumValue);
            }

            if (output != expectedOutput) {
                successful = false;
                failureCause = "iteration " + (i / 5) + " of testLongMethods(), expected output=" + expectedOutput +
                        ", actual=" + output;
            }
        }

        return successful;
    }

    private boolean testSenderDataMethod() {
        // These values test a variety of cases of both text and normalized sender-data strings.
        boolean successful = true;
        Object[] testValues = {
                "Nyzo", array("4e797a6f", 4),
                "X(4e797a6f________________________________________________________)",
                "Nyzo".getBytes(StandardCharsets.UTF_8),
                "X(________________________________________________________________)", array("", 0),
                "AA", array("4141", 2),
                "aa", array("6161", 2),
                "X(AA______________________________________________________________)", array("aa", 1),
                "X(AA__AA__________________________________________________________)", array("582841415f5f4141-" +
                "5f5f5f5f5f5f5f5f-5f5f5f5f5f5f5f5f-5f5f5f5f5f5f5f5f", 32),  // not a valid sender-data string
                "X(AABBCC__________________________________________________________)", array("aabbcc", 3),
                "X(0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef)", array("0123456789abcdef-" +
                "0123456789abcdef-0123456789abcdef-0123456789abcdef", 32),
                "X(0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcd__)", array("0123456789abcdef-" +
                "0123456789abcdef-0123456789abcdef-0123456789abcd", 31),
                "X(0000000000000000000000000000000000000000000000000000000000000000)", new byte[32]
        };

        for (int i = 0; i < testValues.length && successful; i += 2) {
            String input = (String) testValues[i];
            byte[] expectedOutput = (byte[]) testValues[i + 1];
            byte[] output = ClientArgumentUtil.getSenderData(input);

            if (!ByteUtil.arraysAreEqual(output, expectedOutput)) {
                successful = false;
                failureCause = "iteration " + (i / 2) + " of testSenderDataMethod(), expected output=" +
                        ByteUtil.arrayAsStringWithDashes(expectedOutput) + ", actual=" +
                        ByteUtil.arrayAsStringWithDashes(output);
            }
        }

        return successful;
    }

    private static byte[] array(String string, int length) {
        return ByteUtil.byteArrayFromHexString(string, length);
    }

    public String getFailureCause() {
        return failureCause;
    }
}
