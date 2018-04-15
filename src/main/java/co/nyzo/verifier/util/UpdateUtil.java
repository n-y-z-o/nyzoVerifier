package co.nyzo.verifier.util;

public class UpdateUtil {

    private static boolean shouldTerminate = false;

    public static boolean shouldTerminate() {

        return shouldTerminate;
    }

    public static void terminate() {

        System.out.println("termination requested");
        shouldTerminate = true;
    }
}
