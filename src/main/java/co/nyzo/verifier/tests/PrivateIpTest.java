package co.nyzo.verifier.tests;

import co.nyzo.verifier.util.IpUtil;

public class PrivateIpTest {

    public static void main(String[] args) {

        test("192.168.1.1", true);
        test("10.0.0.1", true);
        test("1.2.3.4", false);
        test("172.16.0.0", true);
        test("172.15.0.0", false);
    }

    private static void test(String addressString, boolean isPrivate) {

        byte[] ipAddress = IpUtil.addressFromString(addressString);
        if (isPrivate == IpUtil.isPrivate(ipAddress)) {
            System.out.println(addressString + ": pass");
        } else {
            System.err.println("*****" + addressString + ": FAIL *****");
        }
    }
}
