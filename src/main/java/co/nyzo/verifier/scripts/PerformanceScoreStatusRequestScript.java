package co.nyzo.verifier.scripts;

import co.nyzo.verifier.MessageType;

public class PerformanceScoreStatusRequestScript {

    public static void main(String[] args) {

        ScriptUtil.fetchMultilineStatus(MessageType.PerformanceScoreStatusRequest418, args);
    }
}
