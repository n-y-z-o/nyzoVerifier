package co.nyzo.verifier.tests;

import co.nyzo.verifier.client.ConsoleColor;

public class TestUtil {

    private static final String successDark = ConsoleColor.Green.background() + ConsoleColor.White.bright();
    private static final String successLight = ConsoleColor.Green.backgroundBright() + ConsoleColor.Black;

    private static final String failureDark = ConsoleColor.Red.background() + ConsoleColor.White.bright();
    private static final String failureLight = ConsoleColor.Red.backgroundBright() + ConsoleColor.Black;

    public static void main(String[] args) {

        NyzoTest[] tests = {
                new NyzoStringTest()
        };

        boolean successful = true;
        for (int i = 0; i < tests.length && successful; i++) {
            successful = tests[i].run();
            if (!successful) {
                System.out.println(failureCause(tests[i].getFailureCause()));
            }
        }

        if (successful) {
            System.out.println(successDark + "++ALL TESTS PASSED++" + ConsoleColor.reset);
        }
    }

    public static String passFail(boolean successful) {

        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String[] fullClassName =  stackTrace[2].getClassName().split("\\.");
        String methodDescription = fullClassName[fullClassName.length - 1] + "." + stackTrace[2].getMethodName() + "()";

        String result;
        if (successful) {
            result = successDark + "++PASS++" + successLight + " " + methodDescription + " " + ConsoleColor.reset;
        } else {
            result = failureDark + "--FAIL--" + failureLight + " " + methodDescription + " " + ConsoleColor.reset;
        }

        return result;
    }

    public static String failureCause(String failureCause) {
        return failureDark + "failure cause: " + failureCause + ConsoleColor.reset;
    }
}
