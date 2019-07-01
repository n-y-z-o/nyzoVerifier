package co.nyzo.verifier.web;

import co.nyzo.verifier.RunMode;
import co.nyzo.verifier.client.Client;
import co.nyzo.verifier.micropay.MicropayController;
import co.nyzo.verifier.util.PreferencesUtil;
import co.nyzo.verifier.util.PrintUtil;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
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

    private static Map<String, EndpointMethod> endpointMap = new ConcurrentHashMap<>();

    public static final String contentType = "text/html;charset=UTF-8";

    public static void start() {

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

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {

        // Get the endpoint method and the parameters.
        EndpointMethod endpoint = endpointMap.get(path(httpExchange));
        Map<String, String> parameters = parameters(httpExchange);

        System.out.println("path: " + path(httpExchange));
        System.out.println("parameters: " + parameters(httpExchange));

        // Render the page.
        EndpointResponse response;
        int statusCode;
        if (endpoint == null) {
            response = new EndpointResponse("page not found".getBytes(StandardCharsets.UTF_8));
            statusCode = 404;
        } else {
            response = endpoint.renderByteArray(parameters,
                    httpExchange.getRemoteAddress().getAddress().getAddress());
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

    private static String path(HttpExchange httpExchange) {

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

        return path;
    }

    private static Map<String, String> parameters(HttpExchange httpExchange) {

        // Note that this *does not* work properly for an ampersand (&) is encoded in a parameter. To do this properly,
        // the raw query must be used, and the individual parameters must be decoded.
        Map<String, String> parameters = new HashMap<>();
        try {
            String query = httpExchange.getRequestURI().getQuery();
            if (query != null) {
                String[] querySplit = query.split("&");
                for (int i = 0; i < querySplit.length; i++) {
                    String[] keyValue = querySplit[i].split("=");
                    if (keyValue.length == 2) {
                        String key = keyValue[0].trim();
                        String value = keyValue[1].trim();
                        if (!key.isEmpty() && !value.isEmpty()) {
                            parameters.put(key, value);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            System.out.println(PrintUtil.printException(ignored));
        }

        return parameters;
    }

    private static void buildEndpointMap() {

        // Build the map.
        Map<String, EndpointMethod> map = new ConcurrentHashMap<>();
        RunMode runMode = RunMode.getRunMode();
        if (runMode == RunMode.Sentinel) {
            map.put(SentinelController.pageEndpoint, SentinelController::page);
            map.put(SentinelController.updateEndpoint, SentinelController::update);
        } else if (runMode == RunMode.MicropayServer) {
            // The Micropay controller builds its map dynamically based on the contents of a directory.
            map.putAll(MicropayController.buildEndpointMap());
        } else if (runMode == RunMode.MicropayClient) {
            map.put(MicropayController.clientPingEndpoint, MicropayController::clientPingPage);
            map.put(MicropayController.clientAuthorizationEndpoint, MicropayController::clientAuthorizationPage);
        } else {  // runMode == Client || runMode == Verifier
            map.put("/", CycleController::page);  // will be removed in a later version
            map.put(CycleController.pageEndpoint, CycleController::page);
            map.put(CycleController.updateEndpoint, CycleController::update);
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
        String overrideSuffix;
        if (runMode == RunMode.Verifier) {
            overrideSuffix = "verifier";
        } else if (runMode == RunMode.Sentinel) {
            overrideSuffix = "sentinel";
        } else if (runMode == RunMode.Client) {
            overrideSuffix = "client";
        } else if (runMode == RunMode.MicropayClient) {
            overrideSuffix = "micropay_client";
        } else {  // runMode == RunMode.MicropayServer
            overrideSuffix = "micropay_server";
        }

        // Return the web port.
        int genericPort = PreferencesUtil.getInt("web_port", 80);
        return PreferencesUtil.getInt("web_port_" + overrideSuffix, genericPort);
    }
}
