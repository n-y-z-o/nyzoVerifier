package co.nyzo.verifier.relay;

import co.nyzo.verifier.Verifier;
import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.util.LogUtil;
import co.nyzo.verifier.web.Endpoint;
import co.nyzo.verifier.web.EndpointResponseProvider;
import co.nyzo.verifier.web.HttpMethod;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RelayController {

    private static final File endpointFile = new File(Verifier.dataRootDirectory, "relay_endpoints");

    public static Map<Endpoint, EndpointResponseProvider> buildEndpointMap() {

        Map<Endpoint, EndpointResponseProvider> map = new ConcurrentHashMap<>();
        try {
            List<String> fileContents = Files.readAllLines(Paths.get(endpointFile.getAbsolutePath()));
            for (String line : fileContents) {
                line = line.trim();
                int indexOfHash = line.indexOf("#");
                if (indexOfHash >= 0) {
                    line = line.substring(0, indexOfHash).trim();
                }
                String[] split = line.split(",");
                if (split.length >= 2) {
                    String sourceEndpoint = split[0].trim();
                    String destinationEndpoint = split[1].trim();
                    String host = split.length > 2 ? split[2].trim() : "";

                    if (sourceEndpoint.startsWith("file:")) {
                        // File sources do not contain an update interval. They read directly from the file for
                        // delivery.
                        File file = new File(sourceEndpoint.replace("file:", "").replaceAll("/+", "/"));
                        RelayEndpoint endpoint = new RelayEndpoint(sourceEndpoint);
                        map.put(new Endpoint(destinationEndpoint, HttpMethod.Get, host), endpoint);
                    } else {
                        long updateInterval = 0;
                        try {
                            updateInterval = Long.parseLong(split[2].trim());
                        } catch (Exception ignored) { }
                        if (!sourceEndpoint.isEmpty() && !destinationEndpoint.isEmpty() && updateInterval > 0) {
                            RelayEndpoint endpoint = new RelayEndpoint(sourceEndpoint, updateInterval);
                            map.put(new Endpoint(destinationEndpoint), endpoint);
                            RelayEndpointManager.register(endpoint);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.println(ConsoleColor.Red + "Exception loading relay endpoints from file: " + e.getMessage() +
                    ConsoleColor.reset);
        }

        return map;
    }
}
