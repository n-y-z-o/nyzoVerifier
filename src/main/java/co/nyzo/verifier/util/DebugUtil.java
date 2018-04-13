package co.nyzo.verifier.util;

public class DebugUtil {

    public static String callingMethod() {

        String result = "";

        Throwable throwable = new Throwable();
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        if (stackTrace.length > 2) {
            result = stackTrace[2].getClassName() + "." + stackTrace[2].getMethodName();
        }

        return result;
    }
}
