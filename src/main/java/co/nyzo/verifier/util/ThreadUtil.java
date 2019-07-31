package co.nyzo.verifier.util;

public class ThreadUtil {

    public static void sleep(long milliseconds) {

        if (milliseconds > 0) {
            try {
                Thread.sleep(milliseconds);
            } catch (Exception ignored) { }
        }
    }
}
