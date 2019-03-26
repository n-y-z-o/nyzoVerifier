package co.nyzo.verifier.scripts;

import co.nyzo.verifier.*;

public class ConsensusTallyStatusRequestScript {

    public static void main(String[] args) {

        ScriptUtil.fetchMultilineStatus(MessageType.ConsensusTallyStatusRequest412, args);
    }
}
