package co.nyzo.verifier.tests;

import co.nyzo.verifier.client.CommandManager;
import co.nyzo.verifier.util.PrintUtil;

import java.util.Set;

public class CommandManagerTest implements NyzoTest {

    private String failureCause = null;

    public static void main(String[] args) {

        CommandManagerTest test = new CommandManagerTest();
        boolean successful = test.run();

        if (!successful) {
            System.out.println(TestUtil.failureCause(test.getFailureCause()));
        }
    }

    public boolean run() {

        boolean successful = true;
        try {
            // Get any ambiguous command strings from the command manager.
            Set<String> ambiguousCommandStrings = CommandManager.ambiguousCommandStrings();

            // The test fails if the list of ambiguous command strings is empty.
            if (!ambiguousCommandStrings.isEmpty()) {
                successful = false;
                failureCause = "the following command strings are ambiguous: " + ambiguousCommandStrings;
            }
        } catch (Exception e) {
            failureCause = "exception in " + getClass().getSimpleName() + ": " + PrintUtil.printException(e);
            successful = false;
        }

        System.out.println(TestUtil.passFail(successful));

        return successful;
    }

    public String getFailureCause() {
        return failureCause;
    }
}
