package co.nyzo.verifier.util;

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
            if (exception.getStackTrace().length > 0) {
                StackTraceElement element = exception.getStackTrace()[0];
                result.append("; ").append(element.getClassName()).append(":").append(element.getLineNumber());
            }
        } else {
            result.append("[exception is null]");
        }

        return result.toString();
    }
}
