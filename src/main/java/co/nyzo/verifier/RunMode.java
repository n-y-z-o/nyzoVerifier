package co.nyzo.verifier;

public enum RunMode {

    Verifier,
    Sentinel,
    Client,
    MicropayClient,
    MicropayServer,
    DocumentationServer;

    private static RunMode runMode = null;

    public static RunMode getRunMode() {
        return runMode;
    }

    public static void setRunMode(RunMode runMode) {
        System.err.println("*** setting run mode of " + runMode + " for version " + Version.getVersion() + " ***");
        RunMode.runMode = runMode;
    }
}
