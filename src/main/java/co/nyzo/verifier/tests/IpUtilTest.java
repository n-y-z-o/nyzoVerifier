package co.nyzo.verifier.tests;

import co.nyzo.verifier.RunMode;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PrintUtil;

public class IpUtilTest implements NyzoTest {

    private String failureCause = null;

    public static void main(String[] args) {

        RunMode.setRunMode(RunMode.Test);
        IpUtilTest test = new IpUtilTest();
        boolean successful = test.run();

        if (!successful) {
            System.out.println(TestUtil.failureCause(test.getFailureCause()));
        }
    }

    public boolean run() {

        boolean successful = true;
        try {
            Object[] valuesToTest = {
                    "0.0.0.0", true,
                    "0.0.0.0:", true,
                    "0.0.0.0:65354", true,  // valid, even though the port is an invalid value
                    "1.0.0.256", false,  // invalid due to last byte
                    "1.0.0.0:nyzo rocks", true,  // valid, even though port is non-numerical
                    "1.0.0.0 blarg", true,  // valid despite extraneous characters
                    "1.2.3.4", true,
                    "1.2.3", false,  // not valid due to missing byte
                    "1a.2b.3nyzo.4", true,  // valid despite extraneous characters
                    "my IP address is 192.168.0.1", true  // valid despite extraneous characters
            };

            for (int i = 0; i < valuesToTest.length && successful; i += 2) {
                String ipAddressString = (String) valuesToTest[i];
                boolean expectedValidity = (Boolean) valuesToTest[i + 1];
                successful = IpUtil.isValidAddress(ipAddressString) == expectedValidity;
                if (!successful) {
                    failureCause = ipAddressString + " is expected to be " + (expectedValidity ? "valid" : "invalid") +
                            " but is " + (expectedValidity ? "invalid" : "valid");
                }
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
