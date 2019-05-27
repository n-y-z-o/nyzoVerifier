package co.nyzo.verifier.client.commandHandlers;

import co.nyzo.verifier.client.Command;

import java.util.*;

public class EmptyCommandHandler implements CommandHandler {

    @Override
    public String[] getArgumentNames() {
        return new String[0];
    }

    @Override
    public void run(List<String> argumentValues) {

        Command.printCommands();
    }
}
