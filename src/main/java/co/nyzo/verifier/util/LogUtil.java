package co.nyzo.verifier.util;

import co.nyzo.verifier.RunMode;

public class LogUtil {

    private static final boolean logTimestamps = PreferencesUtil.getBoolean("log_timestamps", false);

    // The "lines" field is initialized to null and assigned in the method below. If the field is statically initialized
    // with an array object, this can cause an initialization race that can cause a null-pointer exception to be thrown
    // when the array is accessed.
    private static final int numberOfLines = 100;
    private static String[] lines = null;
    private static int lineIndex = 0;

    public static void println(String line) {

        if (RunMode.getRunMode() != RunMode.Client) {
            String timestamp = logTimestamps ? "[" + PrintUtil.printTimestamp(System.currentTimeMillis()) + "]: " : "";
            System.out.println(timestamp + line);
        }

        // See above for why assignment here is necessary.
        if (lines == null) {
            lines = new String[numberOfLines];
        }
        lines[lineIndex] = line;
        lineIndex = (lineIndex + 1) % numberOfLines;
    }
}
