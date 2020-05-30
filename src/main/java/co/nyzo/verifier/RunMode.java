package co.nyzo.verifier;

public enum RunMode {

    Client("client"),
    DocumentationServer("documentation_server"),
    RelayServer("relay_server"),
    Sentinel("sentinel"),
    Verifier("verifier");

    private static RunMode runMode = null;

    private String overrideSuffix;

    RunMode(String overrideSuffix) {
        this.overrideSuffix = overrideSuffix;
    }

    public String getOverrideSuffix() {
        return overrideSuffix;
    }

    public static RunMode getRunMode() {
        return runMode;
    }

    public static void setRunMode(RunMode runMode) {
        System.err.println("*** setting run mode of " + runMode + " for version " + Version.getVersion() + " ***");
        RunMode.runMode = runMode;
    }
}
