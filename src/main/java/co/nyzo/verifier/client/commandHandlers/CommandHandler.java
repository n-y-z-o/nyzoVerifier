package co.nyzo.verifier.client.commandHandlers;

import java.util.List;

public interface CommandHandler {

    String[] getArgumentNames();
    void run(List<String> argumentValues);
}
