package co.nyzo.verifier.client;

import co.nyzo.verifier.client.commands.Command;
import co.nyzo.verifier.web.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class CommandEndpointApi implements EndpointResponseProvider {

    private Command command;

    public CommandEndpointApi(Command command) {
        this.command = command;
    }

    @Override
    public EndpointResponse getResponse(EndpointRequest request) {

        // Get an ordered list of argument values.
        List<String> argumentValues = new ArrayList<>();
        for (String identifier : command.getArgumentIdentifiers()) {
            argumentValues.add(request.getQueryParameters().getOrDefault(identifier, ""));
        }

        EndpointResponse response;
        if (command.isLongRunning()) {
            String message = "Long-running commands are not yet supported";
            response = new EndpointResponse(message.getBytes(StandardCharsets.UTF_8));
        } else {
            // Run the command.
            CommandOutput output = new CommandOutputWeb();
            ExecutionResult result = command.run(argumentValues, output);

            // Build the response.
            response = result.toEndpointResponse();
        }

        // Set the header to allow cross-site access.
        response.setHeader("Access-Control-Allow-Origin", "*");

        return response;
    }

    @Override
    public String toString() {
        return "[CommandEndpointApi:" + (command == null ? "(null)" : command.getLongCommand()) + "]";
    }
}
