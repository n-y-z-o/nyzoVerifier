package co.nyzo.verifier.client;

import co.nyzo.verifier.Version;
import co.nyzo.verifier.client.commands.Command;
import co.nyzo.verifier.client.commands.ExitCommand;
import co.nyzo.verifier.web.*;
import co.nyzo.verifier.web.elements.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientController {

    public static final String commandOutputEndpoint = "/commandOutput";
    public static final String commandCompleteString = "***** command complete *****";

    public static Map<Endpoint, EndpointResponseProvider> buildEndpointMap() {

        Map<Endpoint, EndpointResponseProvider> map = new ConcurrentHashMap<>();

        // Add the root for listing all commands.
        map.put(new Endpoint("/"), ClientController::page);

        // Add the command output endpoint. This is used to provide the console output of commands.
        map.put(new Endpoint(commandOutputEndpoint), ClientController::commandOutput);

        // Add the command pages for both GET and POST methods. GET displays a form for the command, and the POST
        // accepts the form for the command.
        for (Command command : CommandManager.getCommands()) {
            map.put(new Endpoint("/" + command.getLongCommand()), new CommandEndpoint(command, HttpMethod.Get));
            map.put(new Endpoint("/" + command.getLongCommand(), HttpMethod.Post),
                    new CommandEndpoint(command, HttpMethod.Post));
        }

        return map;
    }

    public static EndpointResponse page(EndpointRequest request) {

        // Make the HTML page.
        Html html = (Html) new Html().attr("lang", "en");

        // Add the head and body to the page.
        String title = "Nyzo client";
        Head head = (Head) html.add(new Head().addStandardMetadata(title));
        Body body = (Body) html.add(new Body().attr("style", "font-family: sans-serif;"));

        // Add the hover button styles to the head.
        head.add(WebUtil.hoverButtonStyles);

        // Add the title.
        body.add(new H1(title));

        // Add buttons for all commands except exit.
        for (Command command : CommandManager.getCommands()) {
            if (!(command instanceof ExitCommand)) {
                body.add(new A().attr("class", "simple-hover-button").attr("href", "/" + command.getLongCommand())
                        .addRaw(command.getDescription()));
            }
        }

        // Add the footer.
        body.add(new Hr().attr("style", "margin-top: 2rem;"));
        body.add(new P("Nyzo client, version " + Version.getVersion()).attr("style",
                "font-style: italic;"));

        return new EndpointResponse(html.renderByteArray());
    }

    public static EndpointResponse commandOutput(EndpointRequest request) {

        String outputIdentifier = request.getQueryParameters().getOrDefault("id", "");
        CommandOutputWeb output = CommandOutputWebManager.get(outputIdentifier);
        StringBuilder response = new StringBuilder();
        if (output == null) {
            response.append(commandCompleteString);
        } else {
            List<String> lines = output.getOutput();
            String separator = "";
            for (String line : lines) {
                response.append(separator).append(line);
                separator = "<br>";
            }
        }

        return new EndpointResponse(response.toString().getBytes(StandardCharsets.UTF_8));
    }
}
