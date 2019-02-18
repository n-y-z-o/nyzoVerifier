package co.nyzo.verifier.client;

import co.nyzo.verifier.client.commandHandlers.*;

public enum Command {

    BalanceDisplay("BL", "balance", "display the balance of a verifier", new BalanceDisplayCommandHandler()),
    //BlockDisplay("BK", "block", "display a block", new BlockDisplayCommandHandler()),
    //MeshDisplay("M", "mesh", "display the mesh", new MeshDisplayCommandHandler()),
    //VerifierStatus("V", "verifier", "query a verifier for its status", new VerifierStatusCommandHandler()),
    //TransactionSend("T", "send", "send a transaction", new TransactionSendCommandHandler()),
    //RecentTransactions("R", "recent", "display recent transactions and fees related to an account",
    //        new RecentTransactionsCommandHandler()),

    Exit("X", "exit", "exit Nyzo client", new ExitCommandHandler()),
    Invalid("I", "invalid", "an invalid message was provided", new InvalidCommandHandler());

    private String shortCommand;
    private String fullCommand;
    private String description;
    private CommandHandler handler;

    Command(String shortCommand, String fullCommand, String description, CommandHandler handler) {
        this.shortCommand = shortCommand;
        this.fullCommand = fullCommand;
        this.description = description;
        this.handler = handler;
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
}
