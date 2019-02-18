package co.nyzo.verifier.client.commandHandlers;

import co.nyzo.verifier.client.ConsoleUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ExitCommandHandler implements CommandHandler {
    @Override
    public String[] getArgumentNames() {
        return new String[0];
    }

    @Override
    public void run(List<String> argumentValues) {

        String message = "Thank you for using the Nyzo client!";
        ConsoleUtil.printTable(Collections.singletonList(Collections.singletonList(message)));
        UpdateUtil.terminate();
    }
}
