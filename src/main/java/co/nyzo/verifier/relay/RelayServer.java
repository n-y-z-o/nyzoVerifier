package co.nyzo.verifier.relay;

import co.nyzo.verifier.RunMode;
import co.nyzo.verifier.web.WebListener;

public class RelayServer {

    public static void main(String[] args) {

        // The relay server always starts the web listener.
        RunMode.setRunMode(RunMode.RelayServer);
        WebListener.start();
    }
}
