package co.nyzo.verifier;

public enum RunMode {

    Verifier,
    Sentinel,
    Client,
    MicropayClient,
    MicropayServer;

    private static RunMode runMode = null;

    public static RunMode getRunMode() {
        return runMode;
    }

    public static void setRunMode(RunMode runMode) {
        RunMode.runMode = runMode;
    }
}
