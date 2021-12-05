package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.*;

import java.util.ArrayList;
import java.util.List;

public class ClientHealthCommand implements Command {

    @Override
    public String getShortCommand() {
        return "HTH";
    }

    @Override
    public String getLongCommand() {
        return "health";
    }

    @Override
    public String getDescription() {
        return "display health metrics";
    }

    @Override
    public String[] getArgumentNames() {
        return new String[] { };
    }

    @Override
    public String[] getArgumentIdentifiers() {
        return new String[] { };
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

        // Make the lists for the notices and errors. Make the result table.
        List<String> notices = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        CommandTable table = new CommandTable(new CommandTableHeader("# nodes in mesh", "numberNodesInMesh"),
                new CommandTableHeader("# successful block fetches", "numberOfSuccessfulBlockFetches"),
                new CommandTableHeader("# consecutive successful block fetches",
                        "numberOfConsecutiveSuccessfulBlockFetches"),
                new CommandTableHeader("# unsuccessful block fetches", "numberOfUnsuccessfulBlockFetches"),
                new CommandTableHeader("# consecutive unsuccessful block fetches",
                        "numberOfConsecutiveUnsuccessfulBlockFetches"),
                new CommandTableHeader("frozen edge verification age (ms)", "frozenEdgeVerificationAgeMilliseconds"));
        table.setInvertedRowsColumns(true);

        // Add the results to the table.
        Block frozenEdge = BlockManager.getFrozenEdge();
        table.addRow(ClientNodeManager.getNumberOfNodesInMesh(),
                ClientDataManager.getNumberOfSuccessfulBlockFetches(),
                ClientDataManager.getConsecutiveSuccessfulBlockFetches(),
                ClientDataManager.getNumberOfUnsuccessfulBlockFetches(),
                ClientDataManager.getConsecutiveUnsuccessfulBlockFetches(),
                frozenEdge == null ? Double.NaN : (System.currentTimeMillis() - frozenEdge.getVerificationTimestamp()));

        // Add an error if the frozen edge is null.
        if (frozenEdge == null) {
            errors.add("Unable to get frozen edge");
        }

        // Produce the result object.
        return new SimpleExecutionResult(table, notices, errors);
    }
}
