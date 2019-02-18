package co.nyzo.verifier.client.commandHandlers;

import java.util.List;

public class MeshDisplayCommandHandler implements CommandHandler {
    @Override
    public String[] getArgumentNames() {
        return new String[0];
    }

    @Override
    public void run(List<String> argumentValues) {

        System.err.println("<< mesh-status command handler not yet implemented >>");
    }
}
