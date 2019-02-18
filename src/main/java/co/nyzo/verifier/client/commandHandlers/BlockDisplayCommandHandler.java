package co.nyzo.verifier.client.commandHandlers;

import java.util.List;

public class BlockDisplayCommandHandler implements CommandHandler {
    @Override
    public String[] getArgumentNames() {
        //return new String[] { "block height" };
        return new String[0];
    }

    @Override
    public void run(List<String> argumentValues) {

        System.err.println("<< block display command handler not yet implemented >>");
    }
}
