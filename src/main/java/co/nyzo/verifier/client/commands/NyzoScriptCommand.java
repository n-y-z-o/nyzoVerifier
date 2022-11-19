package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.*;
import co.nyzo.verifier.json.*;
import co.nyzo.verifier.nyzoScript.*;
import co.nyzo.verifier.nyzoString.*;
import co.nyzo.verifier.web.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class NyzoScriptCommand implements Command {

    @Override
    public String getShortCommand() {
        return "NS";
    }

    @Override
    public String getLongCommand() {
        return "script";
    }

    @Override
    public String getDescription() {
        return "get the state of a Nyzo script";
    }

    @Override
    public String[] getArgumentNames() {
        return new String[] { "account ID" };
    }

    @Override
    public String[] getArgumentIdentifiers() {
        return new String[] { "accountIdentifier" };
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

        // Lists for notices and errors.
        List<String> notices = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Get the account identifier.
        NyzoStringPublicIdentifier accountIdentifier = ClientArgumentUtil.getPublicIdentifier(argumentValues.get(0));

        // Add the account raw identifier and Nyzo string to the notices.
        notices.add("Account identifier (raw): " + ByteUtil.arrayAsStringWithDashes(accountIdentifier.getBytes()));
        notices.add("Account identifier (Nyzo string): " + NyzoStringEncoder.encode(accountIdentifier));

        // Get the state and produce the result.
        NyzoScriptState state = NyzoScriptManager.stateForAccount(ByteBuffer.wrap(accountIdentifier.getBytes()));
        if (state == null) {
            errors.add("Unable to get state for account " + argumentValues.get(0) + " (" +
                    ByteUtil.arrayAsStringWithDashes(accountIdentifier.getBytes()) + ")");
        }

        CommandTable table = new CommandTable(new CommandTableHeader("creation height", "creationHeight"),
                new CommandTableHeader("last update height", "lastUpdateHeight"),
                new CommandTableHeader("frozen edge height", "frozenEdgeHeight"),
                new CommandTableHeader("content type", "contentType"),
                new CommandTableHeader("contains unconfirmed data", "containsUnconfirmedData"));
        table.setInvertedRowsColumns(true);

        if (state == null) {
            table.addRow("-", "-", "-", "-", "-");
        } else {
            table.addRow(state.getCreationHeight(), state.getLastUpdateHeight(), BlockManager.getFrozenEdgeHeight(),
                    state.getContentType(), state.containsUnconfirmedData());
        }

        return new SimpleExecutionResult(notices, errors, table) {
            @Override
            public EndpointResponse toEndpointResponse() {
                EndpointResponse response;
                if (state == null) {
                    response = new EndpointResponse(JsonRenderer.toJson(this).getBytes(StandardCharsets.UTF_8),
                            EndpointResponse.contentTypeJson);
                } else {
                    response = new EndpointResponse(state.renderJson().getBytes(StandardCharsets.UTF_8),
                            EndpointResponse.contentTypeJson);
                }

                return response;
            }
        };
    }
}
