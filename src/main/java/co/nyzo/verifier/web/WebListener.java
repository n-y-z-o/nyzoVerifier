package co.nyzo.verifier.web;

import co.nyzo.verifier.client.Client;
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
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class WebListener implements HttpHandler {

    public static final String startWebListenerKey = "start_web_listener";

    private static final int port = PreferencesUtil.getInt("web_port", 80);

    private static final Map<String, EndpointMethod> endpointMap = new ConcurrentHashMap<>();

    public static final String contentType = "text/html;charset=UTF-8";

    public static void start() {

        try {
            buildEndpointMap();

            int backlog = 0;
            HttpServer server = HttpServer.create(new InetSocketAddress(port), backlog);
            server.createContext("/", new WebListener());
            server.setExecutor(new ThreadPoolExecutor(2, 4, 20, TimeUnit.SECONDS, new ArrayBlockingQueue<>(20)));
            server.start();
        } catch (Exception e) {
            System.out.println("exception starting web listener: " + PrintUtil.printException(e));
        }
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {

        // Get the endpoint method.
        EndpointMethod endpoint = endpointMap.get(path(httpExchange));

        // Render the page.
        byte[] responseBytes;
        int statusCode;
        if (endpoint == null) {
            responseBytes = "page not found".getBytes(StandardCharsets.UTF_8);
            statusCode = 404;
        } else {
            responseBytes = endpoint.renderByteArray();
            statusCode = 200;
        }

        // Send the response and close the stream.
        int length = responseBytes.length;
        Headers headers = httpExchange.getResponseHeaders();
        headers.set("content-type", contentType);
        httpExchange.sendResponseHeaders(statusCode, length);
        OutputStream responseBody = httpExchange.getResponseBody();
        responseBody.write(responseBytes);
        responseBody.close();
    }

    private static String path(HttpExchange httpExchange) {

        return httpExchange.getRequestURI().getPath();
    }

    private static void buildEndpointMap() {

        add("/", CycleController::cyclePage);  // will be removed in a later version
        add(CycleController.cyclePageEndpoint, CycleController::cyclePage);
        add(CycleController.cycleUpdateEndpoint, CycleController::cycleUpdate);
    }

    private static void add(String path, EndpointMethod method) {
        endpointMap.put(path, method);
    }
}
