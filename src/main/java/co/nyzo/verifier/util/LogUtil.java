package co.nyzo.verifier.util;

import co.nyzo.verifier.RunMode;

public class LogUtil {

    private static final int numberOfLines = 100;
    private static final String[] lines = new String[numberOfLines];
    private static int lineIndex = 0;

    public static void println(String line) {

        if (RunMode.getRunMode() != RunMode.Client) {
            System.out.println(line);
        }

        lines[lineIndex] = line;
        lineIndex = (lineIndex + 1) % numberOfLines;
    }
}
