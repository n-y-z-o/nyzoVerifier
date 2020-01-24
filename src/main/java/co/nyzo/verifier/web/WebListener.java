package co.nyzo.verifier.web;

import co.nyzo.verifier.RunMode;
import co.nyzo.verifier.client.Client;
import co.nyzo.verifier.client.ClientController;
import co.nyzo.verifier.documentation.DocumentationController;
import co.nyzo.verifier.micropay.MicropayController;
import co.nyzo.verifier.util.PreferencesUtil;
import co.nyzo.verifier.util.PrintUtil;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class WebListener implements HttpHandler {

    public static final String startWebListenerKey = "start_web_listener";
    public static final String addWebEndpointsKey = "add_web_endpoints";
    public static final String addApiEndpointsKey = "add_api_endpoints";

    private static Map<Endpoint, EndpointResponseProvider> endpointMap = new ConcurrentHashMap<>();

    public static void start() {

        // Start the listener if the preference indicates starting. The default is true for the documentation server and
        // Micropay modes, false otherwise.
        RunMode runMode = RunMode.getRunMode();
        if (PreferencesUtil.getBoolean(startWebListenerKey, runMode == RunMode.MicropayServer ||
                runMode == RunMode.MicropayClient || runMode == RunMode.DocumentationServer)) {
            try {
                buildEndpointMap();

                int backlog = 0;
                HttpServer server = HttpServer.create(new InetSocketAddress(getPort()), backlog);
                server.createContext("/", new WebListener());
                server.setExecutor(new ThreadPoolExecutor(2, 4, 20, TimeUnit.SECONDS, new ArrayBlockingQueue<>(20)));
                server.start();

                System.out.println("opened listener on port " + server.getAddress().getPort());
            } catch (Exception e) {
                System.out.println("exception starting web listener: " + PrintUtil.printException(e));
            }
        }
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {

        // Get the endpoint method and the parameters.
        EndpointResponseProvider responseProvider = endpointMap.get(endpoint(httpExchange));

        // Render the page.
        EndpointResponse response;
        int statusCode;
        if (responseProvider == null) {
            Endpoint requestedEndpoint = endpoint(httpExchange);
            String responseString = "page not found: path=" + requestedEndpoint.getPath() + ", method=" +
                    requestedEndpoint.getMethod();
            response = new EndpointResponse(responseString.getBytes(StandardCharsets.UTF_8));
            statusCode = 404;
        } else {
            Map<String, String> queryParameters = queryParameters(httpExchange);
            Map<String, String> postParameters = postParameters(httpExchange);
            byte[] ipAddress = httpExchange.getRemoteAddress().getAddress().getAddress();
            EndpointRequest request = new EndpointRequest(queryParameters, postParameters, ipAddress);
            response = responseProvider.getResponse(request);
            statusCode = 200;
        }

        // Send the response and close the stream.
        byte[] responseBytes = response.getContent();
        int length = responseBytes.length;
        String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
        Headers headers = httpExchange.getResponseHeaders();
        headers.set("Date", date);
        for (String name : response.getHeaderNames()) {
            headers.set(name, response.getHeader(name));
        }
        httpExchange.sendResponseHeaders(statusCode, length);
        OutputStream responseBody = httpExchange.getResponseBody();
        responseBody.write(responseBytes);
        responseBody.close();
    }

    private static Endpoint endpoint(HttpExchange httpExchange) {

        // Clean the path. We do not map paths with trailing slashes except for the root.
        String path;
        try {
            path = httpExchange.getRequestURI().getPath();
            if (path == null) {
                path = "/";
            }
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
        } catch (Exception ignored) {
            path = "/";
        }

        // Get the method.
        HttpMethod method = HttpMethod.forString(httpExchange.getRequestMethod());

        return new Endpoint(path, method);
    }

    private static Map<String, String> queryParameters(HttpExchange httpExchange) {
        return mapForString(httpExchange.getRequestURI().getQuery());
    }

    private static Map<String, String> postParameters(HttpExchange httpExchange) {
        return mapForString(readStream(httpExchange.getRequestBody()));
    }

    private static Map<String, String> mapForString(String string) {

        // Note that this *does not* work properly for an ampersand (&) is encoded in a parameter. To do this properly,
        // the raw query must be used, and the individual parameters must be decoded.
        Map<String, String> map = new HashMap<>();
        if (string != null) {
            String[] querySplit = string.split("&");
            for (int i = 0; i < querySplit.length; i++) {
                String[] keyValue = querySplit[i].split("=");
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim();
                    String value = WebUtil.removePercentEncoding(keyValue[1].trim());
                    if (!key.isEmpty() && !value.isEmpty()) {
                        map.put(key, value);
                    }
                }
            }
        }

        return map;
    }

    private static String readStream(InputStream inputStream) {

        StringBuilder result = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            int character;
            while ((character = reader.read()) >= 0) {
                result.appendCodePoint(character);
            }
        } catch (Exception ignored) { }

        return result.toString();
    }

    private static void buildEndpointMap() {

        // Build the map.
        Map<Endpoint, EndpointResponseProvider> map = new ConcurrentHashMap<>();
        RunMode runMode = RunMode.getRunMode();
        switch (runMode) {
            case Client:
                map.putAll(ClientController.buildEndpointMap());
                break;
            case DocumentationServer:
                map.putAll(DocumentationController.buildEndpointMap());
                break;
            case MicropayClient:
                map.put(MicropayController.clientPingEndpoint, MicropayController::clientPingPage);
                map.put(MicropayController.clientAuthorizationEndpoint, MicropayController::clientAuthorizationPage);
                break;
            case MicropayServer:
                // Add the ping endpoint. Add this before the dynamic pages to allow dynamic override.
                map.put(MicropayController.serverPingEndpoint, MicropayController::serverPingPage);

                // The Micropay controller builds its map dynamically based on the contents of a directory.
                map.putAll(MicropayController.buildEndpointMap());
                break;
            case Sentinel:
                map.put(SentinelController.pageEndpoint, SentinelController::page);
                map.put(SentinelController.updateEndpoint, SentinelController::update);
                break;
            case Verifier:
                map.put(new Endpoint("/"), CycleController::page);  // will be removed in a later version
                map.put(CycleController.pageEndpoint, CycleController::page);
                map.put(CycleController.updateEndpoint, CycleController::update);
                break;
        }

        // Assign the map to the static variable. Building and swapping results in an atomic update of the endpoints.
        endpointMap = map;
    }

    private static int getPort() {

        // To allow for more flexibility while retaining the same behavior as previous versions, 'web_port' is still
        // used to specify the port for all run modes. However, run-mode-specific versions are also allowed, and they
        // override the generic setting. This is especially helpful for development, as both a Micropay server and
        // client can be run on the same host.
        RunMode runMode = RunMode.getRunMode();

        // Return the web port.
        int genericPort = PreferencesUtil.getInt("web_port", 80);
        return PreferencesUtil.getInt("web_port_" + runMode.getOverrideSuffix(), genericPort);
    }
}
