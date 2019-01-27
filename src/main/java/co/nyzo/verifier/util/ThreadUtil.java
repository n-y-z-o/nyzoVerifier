package co.nyzo.verifier.util;

public class ThreadUtil {

    public static void sleep(long milliseconds) {

        try {
            Thread.sleep(Math.max(0L, milliseconds));
        } catch (Exception ignored) { }
    }
}
