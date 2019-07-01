package co.nyzo.verifier.micropay;

import co.nyzo.verifier.RunMode;
import co.nyzo.verifier.web.WebListener;

public class MicropayClient {

    public static void main(String[] args) {
        RunMode.setRunMode(RunMode.MicropayClient);
        WebListener.start();
    }
}
