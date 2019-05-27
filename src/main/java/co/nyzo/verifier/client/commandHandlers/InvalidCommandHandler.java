package co.nyzo.verifier.client.commandHandlers;

import co.nyzo.verifier.client.Command;
import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.client.ConsoleUtil;

import java.util.*;

public class InvalidCommandHandler implements CommandHandler {

    @Override
    public String[] getArgumentNames() {
        return new String[0];
    }

    @Override
    public void run(List<String> argumentValues) {

        Command.printCommands();

        System.out.println(ConsoleColor.Red + "Your selection was not recognized." + ConsoleColor.reset);
        System.out.println(ConsoleColor.Red + "Please choose an option from the above commands." + ConsoleColor.reset);
        System.out.println(ConsoleColor.Red + "You may type either the short command or the full command." +
                ConsoleColor.reset);
    }
}
