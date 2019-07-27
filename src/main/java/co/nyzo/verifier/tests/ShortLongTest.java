package co.nyzo.verifier.tests;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.ShortLong;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class ShortLongTest implements NyzoTest {

    private String failureCause = null;

    public static void main(String[] args) {

        ShortLongTest test = new ShortLongTest();
        boolean successful = test.run();

        if (!successful) {
            System.out.println(TestUtil.failureCause(test.getFailureCause()));
        }
    }

    public boolean run() {

        boolean successful;
        try {
            // For reasonable coverage, we try minimum and maximum unsigned values of both shorts and longs, along with
            // a number of random values in the range.
            Set<Integer> shortValuesToTest = new HashSet<>();
            shortValuesToTest.add(0);
            shortValuesToTest.add(ShortLong.maximumShortValue);

            Set<Long> longValuesToTest = new HashSet<>();
            longValuesToTest.add(0L);
            longValuesToTest.add(ShortLong.maximumLongValue);

            Random random = new Random(977);
            for (int i = 0; i < 200; i++) {
                shortValuesToTest.add(random.nextInt(ShortLong.maximumShortValue));

                long longValue = Math.abs(random.nextLong());
                while (longValue > ShortLong.maximumLongValue) {
                    longValue /= 2;
                }
                longValuesToTest.add(longValue);
            }

            // Try all combinations of short values and long values.
            successful = true;
            for (Integer shortValue : shortValuesToTest) {
                for (Long longValue : longValuesToTest) {

                    long combinedValue = ShortLong.combinedValue(shortValue, longValue);

                    ByteBuffer buffer = ByteBuffer.wrap(new byte[FieldByteSize.combinedVersionAndHeight]);
                    buffer.putLong(combinedValue);
                    buffer.rewind();
                    ShortLong shortLong = ShortLong.fromByteBuffer(buffer);
                    if (shortLong.getShortValue() != shortValue) {
                        successful = false;
                        failureCause = "input short value, " + shortValue + " does not match output short value " +
                                shortLong.getShortValue();
                    }

                    // Check the values formatted as strings. This is a redundant check, but redundancies in tests are
                    // welcome assurances. Also, the values produced here would be useful in debugging.
                    String separateString = String.format("%04x%012x", shortValue, longValue);
                    String combinedString = String.format("%016x", combinedValue);
                    if (!separateString.equals(combinedString)) {
                        successful = false;
                        failureCause = "hex string of separate values, " + separateString + ", does not match hex " +
                                "string of combined value, " + combinedString;
                    }
                }
            }

            // For backward compatibility, ensure that values with a short value of zero equal the raw long value.
            for (int i = 0; i < 1000; i++) {
                long longValue = Math.abs(random.nextLong());
                while (longValue > ShortLong.maximumLongValue) {
                    longValue /= 2;
                }

                long combinedValue = ShortLong.combinedValue(0, longValue);
                if (combinedValue != longValue) {
                    successful = false;
                    failureCause = "combined value, " + combinedValue + ", does not equal raw long value, " +
                            longValue + ", for short value of 0";
                }
            }

        } catch (Exception e) {
            failureCause = "exception in ShortLongTest: " + PrintUtil.printException(e);
            successful = false;
        }

        System.out.println(TestUtil.passFail(successful));

        return successful;
    }

    public String getFailureCause() {
        return failureCause;
    }
}
