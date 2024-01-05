package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.*;

import java.util.ArrayList;
import java.util.List;

public class FrozenEdgeCommand implements Command {

    @Override
    public String getShortCommand() {
        return "FE";
    }

    @Override
    public String getLongCommand() {
        return "frozenEdge";
    }

    @Override
    public String getDescription() {
        return "display the frozen edge";
    }

    @Override
    public String[] getArgumentNames() {
        return new String[0];
    }

    @Override
    public String[] getArgumentIdentifiers() {
        return new String[0];
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

        // Make the lists and table for the result.
        List<String> notices = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        CommandTable table = new CommandTable(new CommandTableHeader("height", "height"),
                new CommandTableHeader("hash", "hash", true),
                new CommandTableHeader("verification timestamp (ms)", "verificationTimestampMilliseconds", false),
                new CommandTableHeader("distance from open edge", "distanceFromOpenEdge"),
                new CommandTableHeader("clientVersion", "clientVersion"));
        table.setInvertedRowsColumns(true);

        // Get the block and the balance list.
        Block block = BlockManager.getFrozenEdge();

        // Add the data to the table.
        if (block == null) {
            errors.add("Unable to get frozen edge");
        } else {
            table.addRow(block.getBlockHeight(), ByteUtil.arrayAsStringWithDashes(block.getHash()),
                    block.getVerificationTimestamp(), (int) (BlockManager.openEdgeHeight(false) -
                            block.getBlockHeight()), Version.getVersion() + "." + Version.getSubVersion());
        }

        // Return the result.
        return new SimpleExecutionResult(notices, errors, table);
    }
}
