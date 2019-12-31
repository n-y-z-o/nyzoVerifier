package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.KeyUtil;
import co.nyzo.verifier.client.*;
import co.nyzo.verifier.nyzoString.NyzoStringEncoder;
import co.nyzo.verifier.nyzoString.NyzoStringPrivateSeed;
import co.nyzo.verifier.nyzoString.NyzoStringPublicIdentifier;

import java.util.List;

public class PrivateNyzoStringCommand implements Command {

    @Override
    public String getShortCommand() {
        return "NSS";
    }

    @Override
    public String getLongCommand() {
        return "seedString";
    }

    @Override
    public String getDescription() {
        return "create Nyzo strings for a private seed";
    }

    @Override
    public String[] getArgumentNames() {
        return new String[] { "private key" };
    }

    @Override
    public String[] getArgumentIdentifiers() {
        return new String[] { "privateKey" };
    }

    @Override
    public boolean requiresValidation() {
        return false;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public boolean isLongRunning() {
        return false;
    }

    @Override
    public ValidationResult validate(List<String> argumentValues, CommandOutput output) {
        return null;
    }

    @Override
    public ExecutionResult run(List<String> argumentValues, CommandOutput output) {

        byte[] privateSeed = ByteUtil.byteArrayFromHexString(argumentValues.get(0), FieldByteSize.seed);
        NyzoStringPrivateSeed privateSeedString = new NyzoStringPrivateSeed(privateSeed);

        byte[] publicIdentifier = KeyUtil.identifierForSeed(privateSeed);
        NyzoStringPublicIdentifier publicIdentifierString = new NyzoStringPublicIdentifier(publicIdentifier);

        // Build the output table.
        CommandTable table = new CommandTable(new CommandTableHeader("private seed (raw)", "privateSeedBytes"),
                new CommandTableHeader("private seed (Nyzo string)", "privateSeedNyzoString"),
                new CommandTableHeader("public ID (raw)", "publicIdBytes"),
                new CommandTableHeader("public ID (Nyzo string)", "publicIdNyzoString"));
        table.setInvertedRowsColumns(true);
        table.addRow(ByteUtil.arrayAsStringWithDashes(privateSeed), NyzoStringEncoder.encode(privateSeedString),
                ByteUtil.arrayAsStringWithDashes(publicIdentifier), NyzoStringEncoder.encode(publicIdentifierString));

        // Produce the execution result.
        return new SimpleExecutionResult(table, null, null);
    }

    public static void printHexWarning(CommandOutput output) {
        PrivateNyzoStringCommand command = new PrivateNyzoStringCommand();
        output.println(ConsoleColor.Yellow.background() + "You appear to be using a raw hexadecimal " +
                "private key. Please convert this to a Nyzo string with the \"" + command.getLongCommand() +
                "\" (" + command.getShortCommand() + ") command." + ConsoleColor.reset);
    }
}
