package co.nyzo.verifier.util;

import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.Transaction;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class PrintUtil {

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    static {
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public static String printAmount(long micronyzos) {

        boolean amountIsNegative = micronyzos < 0;
        micronyzos = Math.abs(micronyzos);
        long whole = micronyzos / Transaction.micronyzoMultiplierRatio;
        long fraction = micronyzos % Transaction.micronyzoMultiplierRatio;
        return String.format(amountIsNegative ? "(∩%d.%06d)" : "∩%d.%06d", whole, fraction);
    }

    public static String printAmountAsMicronyzos(long micronyzos) {

        return String.format("µ%d", micronyzos);
    }

    public static String printTimestamp(long timestamp) {

        long whole = timestamp / 1000L;
        long fraction = timestamp % 1000L;
        Date date = new Date(timestamp);
        return String.format("%d.%03d (%s UTC)", whole, fraction, dateFormat.format(date));
    }

    public static String printException(Exception exception) {

        StringBuilder result = new StringBuilder("exception: ");
        if (exception != null) {
            result.append(exception.getMessage());
            String separator = "; ";
            for (int i = 0; i < Math.min(7, exception.getStackTrace().length); i++) {
                StackTraceElement element = exception.getStackTrace()[i];
                result.append(separator).append(element.getClassName()).append(":").append(element.getLineNumber());
                separator = ", ";
            }
        } else {
            result.append("[exception is null]");
        }

        return result.toString();
    }

    public static String compactPrintByteArray(byte[] array) {

        String result;
        if (array == null) {
            result = "(null)";
        } else if (array.length == 0) {
            result = "(empty)";
        } else if (array.length <= 4) {
            result = ByteUtil.arrayAsStringNoDashes(array);
        } else {
            result = String.format("%02x%02x...%02x%02x", array[0], array[1], array[array.length - 2],
                    array[array.length - 1]);
        }

        return result;
    }

    public static String superCompactPrintByteArray(byte[] array) {

        String result;
        if (array == null) {
            result = "(null)";
        } else if (array.length == 0) {
            result = "(empty)";
        }  else if (array.length <= 2) {
            result = ByteUtil.arrayAsStringNoDashes(array);
        } else {
            result = String.format("%02x...%02x", array[0], array[array.length - 1]);
        }

        return result;
    }
}
