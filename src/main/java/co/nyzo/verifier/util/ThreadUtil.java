package co.nyzo.verifier.util;

public class ThreadUtil {

    public static void sleep(long milliseconds) {

        try {
            Thread.sleep(milliseconds);
        } catch (Exception ignored) { }
    }
}
