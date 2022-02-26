package co.nyzo.verifier.client;

import co.nyzo.verifier.BlockManager;
import co.nyzo.verifier.Verifier;
import co.nyzo.verifier.Version;
import co.nyzo.verifier.client.commands.Command;
import co.nyzo.verifier.client.commands.ExitCommand;
import co.nyzo.verifier.util.PreferencesUtil;
import co.nyzo.verifier.web.*;
import co.nyzo.verifier.web.elements.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientController {

    public static final String commandOutputEndpoint = "/commandOutput";
    public static final String commandCompleteString = "***** command complete *****";

    private static final File noteFile = new File(Verifier.dataRootDirectory, "client-note.html");

    public static Map<Endpoint, EndpointResponseProvider> buildEndpointMap() {

        Map<Endpoint, EndpointResponseProvider> map = new ConcurrentHashMap<>();

        // If the preference is set, add the web endpoints.
        if (PreferencesUtil.getBoolean(WebListener.addWebEndpointsKey, true)) {
            // Add the root for listing all commands.
            map.put(new Endpoint("/"), ClientController::page);

            // Add the command output endpoint. This is used to provide the console output of commands.
            map.put(new Endpoint(commandOutputEndpoint), ClientController::commandOutput);

            // Add the command pages for both GET and POST methods. GET displays a form for the command, and the POST
            // accepts the form for the command.
            for (Command command : CommandManager.getCommands()) {
                if (!(command instanceof ExitCommand)) {
                    map.put(new Endpoint("/" + command.getLongCommand()), new CommandEndpointWeb(command,
                            HttpMethod.Get));
                    map.put(new Endpoint("/" + command.getLongCommand(), HttpMethod.Post),
                            new CommandEndpointWeb(command, HttpMethod.Post));
                }
            }
        }

        // If the preference is set, add the API endpoints.
        if (PreferencesUtil.getBoolean(WebListener.addApiEndpointsKey, true)) {
            // TODO: Add an endpoint for asynchronous status updates.

            // Add an endpoint for each command that is not long-running. When asynchronous status updates are
            // implemented, long-running commands will be added.
            for (Command command : CommandManager.getCommands()) {
                if (!(command instanceof ExitCommand) && !command.isLongRunning()) {
                    map.put(new Endpoint("/api/" + command.getLongCommand()), new CommandEndpointApi(command));
                }
            }
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

        // Add the client note, if available.
        List<String> noteFileContents = null;
        try {
            noteFileContents = Files.readAllLines(Paths.get(noteFile.getAbsolutePath()));
        } catch (Exception ignored) { }
        if (noteFileContents != null) {
            StringBuilder noteFileMerged = new StringBuilder();
            String separator = "";
            for (String line : noteFileContents) {
                noteFileMerged.append(separator).append(line);
                separator = " ";
            }
            body.addRaw(noteFileMerged.toString());
        }

        // Add buttons for all commands except exit.
        for (Command command : CommandManager.getCommands()) {
            if (!(command instanceof ExitCommand)) {
                body.add(new A().attr("class", "hover-button").attr("href", "/" + command.getLongCommand())
                        .addRaw(command.getDescription()));
            }
        }

        // Add the footer.
        body.add(new Hr().attr("style", "margin-top: 2rem;"));
        body.add(new P("Nyzo client, version " + Version.getVersion()).attr("style", "font-style: italic;")
                .attr("id", "client-version"));

        long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
        long distanceBehindOpen = BlockManager.openEdgeHeight(false) - frozenEdgeHeight;
        body.add(new P("frozen edge: " + frozenEdgeHeight + " (" + distanceBehindOpen + " behind open)")
                .attr("style", "font-style: italic;").attr("id", "frozen-edge"));

        // Add script for updating the version and frozen edge in the footer. This uses the /api/frozenEdge endpoint.
        body.add(new Script("" +
                "setInterval(function() {" +
                "    var request = new XMLHttpRequest();" +
                "    request.onreadystatechange = function() {" +
                "        if (this.readyState == XMLHttpRequest.DONE && this.status == 200) {" +
                "            var data = JSON.parse(this.responseText);" +
                "            document.getElementById('client-version').innerHTML = 'Nyzo client, version ' + " +
                "                data['result'][0]['clientVersion'];" +
                "            document.getElementById('frozen-edge').innerHTML = 'frozen edge: ' + " +
                "                data['result'][0]['height'] + ' (' + data['result'][0]['distanceFromOpenEdge'] + " +
                "                ' behind open)';" +
                "        }" +
                "    };" +
                "    request.open('GET', '/api/frozenEdge', true);" +
                "    request.send();" +
                "}, 7000);"));

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
