package co.nyzo.verifier.tests;

import co.nyzo.verifier.messages.BootstrapResponse;

public class BootstrapResponseTest {

    public static void main(String[] args) {

        BootstrapResponse response = new BootstrapResponse();
        System.out.println("cycle lengths: " + response.getCycleLengths());
    }
}
