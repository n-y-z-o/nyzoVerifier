package co.nyzo.verifier.util;

class DebugUtil {

    public static String callingMethod() {

        String result = "";

        Throwable throwable = new Throwable();
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        if (stackTrace.length > 2) {
            result = stackTrace[2].getClassName() + "." + stackTrace[2].getMethodName();
        }

        return result;
    }

    public static boolean fromClass(String className) {

        boolean fromClass = false;
        Throwable throwable = new Throwable();
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        for (int i = 1; i < stackTrace.length; i++) {
            if (stackTrace[i].getClassName().contains(className)) {
                fromClass = true;
            }
        }

        return fromClass;
    }

    public static String callingMethods(int length) {

        StringBuilder result = new StringBuilder();

        Throwable throwable = new Throwable();
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        String separator = "";
        for (int i = 1; i < 1 + length && i < stackTrace.length; i++) {
            String filename = stackTrace[i].getFileName();
            if (filename == null) {
                filename = "<null>";
            } else if (filename.length() > 5) {
                filename = filename.substring(0, filename.length() - 5);
            }
            result.append(separator).append(filename).append(".").append(stackTrace[i].getMethodName());
            result.append("(").append(stackTrace[i].getLineNumber()).append(")");
            separator = ",";
        }

        return result.toString();
    }
}
