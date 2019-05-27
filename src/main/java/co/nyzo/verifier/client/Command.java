package co.nyzo.verifier.client;

import co.nyzo.verifier.client.commandHandlers.*;

import java.util.*;

public enum Command {

    BalanceDisplay("BL", "balance", "display the balance of a verifier", new BalanceDisplayCommandHandler(), true),
    //BlockDisplay("BK", "block", "display a block", new BlockDisplayCommandHandler(), true),
    //MeshDisplay("M", "mesh", "display the mesh", new MeshDisplayCommandHandler(), true),
    //VerifierStatus("V", "verifier", "query a verifier for its status", new VerifierStatusCommandHandler(), true),
    //TransactionSend("T", "send", "send a transaction", new TransactionSendCommandHandler(), true),
    //RecentTransactions("R", "recent", "display recent transactions and fees related to an account",
    //        new RecentTransactionsCommandHandler(), true),

    Exit("X", "exit", "exit Nyzo client", new ExitCommandHandler(), true),
    Invalid("I", "invalid", "an invalid message was provided", new InvalidCommandHandler(), false),
    Empty("", "empty", "an empty message was provided", new EmptyCommandHandler(), false);

    private String shortCommand;
    private String fullCommand;
    private String description;
    private CommandHandler handler;
    private boolean inCommandList;

    Command(String shortCommand, String fullCommand, String description, CommandHandler handler,
            boolean inCommandList) {
        this.shortCommand = shortCommand;
        this.fullCommand = fullCommand;
        this.description = description;
        this.handler = handler;
        this.inCommandList = inCommandList;
    }

    public String getShortCommand() {
        return shortCommand;
    }

    public String getFullCommand() {
        return fullCommand;
    }

    public String getDescription() {
        return description;
    }

    public CommandHandler getHandler() {
        return handler;
    }

    public boolean isInCommandList() {
        return inCommandList;
    }

    public static void printCommands() {

        // Build the columns.
        List<List<String>> columns = new ArrayList<>();
        columns.add(new ArrayList<>(Arrays.asList("short", "command")));
        columns.add(new ArrayList<>(Arrays.asList("full", "command")));
        columns.add(new ArrayList<>(Arrays.asList("", "description")));
        for (Command command : values()) {
            if (command.isInCommandList()) {
                columns.get(0).add(command.getShortCommand());
                columns.get(1).add(command.getFullCommand());
                columns.get(2).add(command.getDescription());
            }
        }

        // Print the table.
        ConsoleUtil.printTable(columns, new HashSet<>(Collections.singleton(1)));
    }
}
