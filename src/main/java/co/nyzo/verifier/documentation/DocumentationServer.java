package co.nyzo.verifier.documentation;

import co.nyzo.verifier.RunMode;
import co.nyzo.verifier.web.WebListener;

public class DocumentationServer {

    public static void main(String[] args) {

        // The documentation server always starts the web listener.
        RunMode.setRunMode(RunMode.DocumentationServer);
        WebListener.start();
    }
}
