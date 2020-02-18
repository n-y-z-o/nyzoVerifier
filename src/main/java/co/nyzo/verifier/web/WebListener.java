package co.nyzo.verifier.web;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.Client;
import co.nyzo.verifier.client.ClientController;
import co.nyzo.verifier.documentation.DocumentationController;
import co.nyzo.verifier.micropay.MicropayController;
import co.nyzo.verifier.util.*;

import javax.net.ssl.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

public class WebListener {

    private static final String startWebListenerKey = "start_web_listener";
    private static final String keystorePathKey = "web_listener_keystore_path";
    private static final String keystorePasswordKey = "web_listener_keystore_password";

    public static final String addWebEndpointsKey = "add_web_endpoints";
    public static final String addApiEndpointsKey = "add_api_endpoints";

    private static final AtomicLong numberOfMessagesRejected = new AtomicLong(0);
    private static final AtomicLong numberOfMessagesAccepted = new AtomicLong(0);

    private static final Map<ByteBuffer, Integer> connectionsPerIp = new ConcurrentHashMap<>();
    private static final AtomicInteger activeReadThreads = new AtomicInteger(0);

    private static final int maximumConcurrentConnectionsForIp =
            PreferencesUtil.getInt("web_maximum_concurrent_connections_per_ip", 40);

    private static final File temporaryForwardingWebDirectory = new File(Verifier.dataRootDirectory, "webTemp");

    private static final BiFunction<Integer, Integer, Integer> mergeFunction =
            new BiFunction<Integer, Integer, Integer>() {
                @Override
                public Integer apply(Integer integer0, Integer integer1) {
                    int value0 = integer0 == null ? 0 : integer0;
                    int value1 = integer1 == null ? 0 : integer1;
                    return value0 + value1;
                }
            };

    private static Map<Endpoint, EndpointResponseProvider> endpointMap = new ConcurrentHashMap<>();

    public static void start() {

        // Start the listener if the preference indicates starting. The default is true for the documentation server and
        // Micropay modes, false otherwise.
        RunMode runMode = RunMode.getRunMode();
        if (PreferencesUtil.getBoolean(startWebListenerKey, runMode == RunMode.MicropayServer ||
                runMode == RunMode.MicropayClient || runMode == RunMode.DocumentationServer)) {
            buildEndpointMap();
            openHttpListener();
            openHttpsListener();
        }
    }

    private static void openHttpListener() {
        try {
            ServerSocket serverSocket = new ServerSocket(getPort());
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (!UpdateUtil.shouldTerminate()) {
                        try {
                            Socket clientSocket = serverSocket.accept();
                            processSocket(clientSocket);
                        } catch (Exception ignored) { }
                    }
                }
            }).start();

            LogUtil.println("opened HTTP listener on port " + serverSocket.getLocalPort());
        } catch (Exception e) {
            LogUtil.println("exception starting HTTP web listener: " + PrintUtil.printException(e));
        }
    }

    private static void openHttpsListener() {
        String keystorePath = PreferencesUtil.get(keystorePathKey);
        String keystorePassword = PreferencesUtil.get(keystorePasswordKey);
        if (keystorePath != null && !keystorePath.isEmpty() && keystorePassword != null &&
                !keystorePassword.isEmpty()) {
            try {
                // Load the keystore.
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                InputStream inputStream = new FileInputStream(keystorePath);
                keyStore.load(inputStream, keystorePassword.toCharArray());

                // Initialize the trust manager factory and key manager factory with the keystore.
                TrustManagerFactory trustManagerFactory =
                        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(keyStore);

                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
                keyManagerFactory.init(keyStore, keystorePassword.toCharArray());

                // Initialize the SSL context with the key manager and trust manager.
                SSLContext context = SSLContext.getInstance("TLSv1.2");
                context.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);

                // Get the SSL server socket factory from the SSL context.
                SSLServerSocketFactory factory = context.getServerSocketFactory();

                // Create the SSL server socket and start the read loop.
                SSLServerSocket sslServerSocket = (SSLServerSocket) factory.createServerSocket(443);
                sslServerSocket.setEnabledProtocols(new String[] { "TLSv1.2" });
                sslServerSocket.setEnabledCipherSuites(WebListenerCipherSuites.strongCipherSuites);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while (!UpdateUtil.shouldTerminate()) {
                            try {
                                Socket clientSocket = sslServerSocket.accept();
                                processSocket(clientSocket);
                            } catch (Exception ignored) { }
                        }
                    }
                }).start();
            } catch (Exception e) {
                LogUtil.println("exception starting HTTPS web listener: " + PrintUtil.printException(e));
            }
        }
    }

    private static void processSocket(Socket clientSocket) {

        byte[] ipAddress = clientSocket.getInetAddress().getAddress();
        if (BlacklistManager.inBlacklist(ipAddress)) {
            numberOfMessagesRejected.incrementAndGet();
            ConnectionManager.fastCloseSocket(clientSocket);
        } else {
            ByteBuffer ipBuffer = ByteBuffer.wrap(ipAddress);
            int connectionsForIp = connectionsPerIp.merge(ipBuffer, 1, mergeFunction);

            if (connectionsForIp > maximumConcurrentConnectionsForIp && !Message.ipIsWhitelisted(ipAddress)) {

                LogUtil.println("blacklisting IP " + IpUtil.addressAsString(ipAddress) +
                        " due to too many concurrent connections");

                // Decrement the counter, add the IP to the blacklist, and close the socket without responding.
                connectionsPerIp.merge(ipBuffer, -1, mergeFunction);
                BlacklistManager.addToBlacklist(ipAddress);
                ConnectionManager.fastCloseSocket(clientSocket);

            } else {

                // Read the message and respond.
                numberOfMessagesAccepted.incrementAndGet();
                activeReadThreads.incrementAndGet();
                new Thread(new Runnable() {
                    @Override
                    public void run() {

                        try {
                            clientSocket.setSoTimeout(2000);
                            boolean socketAlive = true;
                            while (socketAlive) {
                                socketAlive = readMessageAndRespond(clientSocket);
                            }

                            ConnectionManager.slowCloseSocket(clientSocket);
                        } catch (Exception ignored) { }

                        // Decrement the counter for this IP.
                        connectionsPerIp.merge(ipBuffer, -1, mergeFunction);

                        if (activeReadThreads.decrementAndGet() == 0) {

                            // When the number of active threads is zero, clear the map of
                            // connections per IP to prevent accumulation of too many IP
                            // addresses over time.
                            connectionsPerIp.clear();
                        }
                    }
                }, "WebListener-clientSocket").start();
            }
        }
    }

    private static boolean readMessageAndRespond(Socket clientSocket) {

        boolean socketAlive = false;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            String[] requestParameters = reader.readLine().split("\\s+");
            if (requestParameters.length >= 2) {
                // Get the method, path, and query string.
                HttpMethod method = HttpMethod.forString(requestParameters[0]);
                String pathParameter = requestParameters[1];
                int questionMarkIndex = pathParameter.indexOf('?');
                String path;
                String queryString;
                if (questionMarkIndex >= 0) {
                    path = pathParameter.substring(0, questionMarkIndex);
                    queryString = pathParameter.substring(questionMarkIndex + 1);
                } else {
                    path = pathParameter;
                    queryString = "";
                }

                // Remove all double dots from the path to avoid attempts at escaping from the web directory.
                path = path.replace("..", "");

                // If POST, get the body.
                String postBody = "";
                if (method == HttpMethod.Post) {
                    // Get the content length from the headers.
                    String line;
                    int contentLength = 0;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        if (line.toLowerCase().startsWith("content-length: ")) {
                            try {
                                contentLength = Integer.parseInt(line.substring("content-length: ".length()));
                            } catch (Exception ignored) { }
                        }
                    }

                    // Read the body into a string.
                    char[] buffer = new char[contentLength];
                    reader.read(buffer);
                    postBody = new String(buffer);
                }

                // Build the request object.
                Endpoint endpoint = new Endpoint(path, method);
                Map<String, String> queryParameters = mapForString(queryString);
                Map<String, String> postParameters = mapForString(postBody);
                byte[] sourceIpAddress = clientSocket.getInetAddress().getAddress();
                EndpointRequest request = new EndpointRequest(endpoint, queryParameters, postParameters,
                        sourceIpAddress);

                // Get the response.
                EndpointResponse response = getResponse(request);

                // Get the output stream.
                BufferedOutputStream outputStream = new BufferedOutputStream(clientSocket.getOutputStream());

                // Write the status header.
                Charset charset = StandardCharsets.US_ASCII;
                outputStream.write(("HTTP/1.1 " + response.getStatusCode().getCode() + " " +
                        response.getStatusCode().getLabel() + "\r\n").getBytes(charset));

                // Write the date header.
                String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC));
                outputStream.write(("Date: " + date + "\r\n").getBytes(charset));

                // Write the length header.
                byte[] responseBytes = response.getContent();
                int length = responseBytes.length;
                outputStream.write(("Content-length: " + responseBytes.length + "\r\n").getBytes(charset));

                // Write the headers contained in the response object.
                for (String key : response.getHeaderNames()) {
                    outputStream.write((key + ": " + response.getHeader(key) + "\r\n").getBytes(charset));
                }

                outputStream.write(("\r\n").getBytes(charset));
                outputStream.write(responseBytes);
                outputStream.flush();
            }

            socketAlive = true;
        } catch (Exception ignored) { }

        return socketAlive;
    }

    public static EndpointResponse getResponse(EndpointRequest request) {

        // Get the endpoint method and the parameters.
        EndpointResponseProvider responseProvider = endpointMap.get(request.getEndpoint());

        // Render the response.
        EndpointResponse response;
        int statusCode;
        if (responseProvider == null) {
            if (temporaryForwardingWebDirectory.exists()) {
                response = getTemporaryForwardingResponse(request);
            } else {
                Endpoint requestedEndpoint = request.getEndpoint();
                String responseString = "page not found: path=" + requestedEndpoint.getPath() + ", method=" +
                        requestedEndpoint.getMethod();
                response = new EndpointResponse(responseString.getBytes(StandardCharsets.UTF_8),
                        EndpointResponse.contentTypeText, HttpStatusCode.NotFound404);
            }
        } else {
            response = responseProvider.getResponse(request);
        }

        // Return the result.
        return response;
    }

    private static EndpointResponse getTemporaryForwardingResponse(EndpointRequest request) {

        // This is a special response to allow creation or renewal of a Let's Encrypt certificate.
        File file = new File(temporaryForwardingWebDirectory, request.getEndpoint().getPath());
        byte[] responseBytes = null;
        if (file.exists()) {
            try {
                responseBytes = Files.readAllBytes(Paths.get(file.getAbsolutePath()));
            } catch (Exception ignored) { }
        }

        HttpStatusCode statusCode;
        if (responseBytes == null || responseBytes.length == 0) {
            responseBytes = ("file not found: " + request.getEndpoint().getPath()).getBytes(StandardCharsets.UTF_8);
            statusCode = HttpStatusCode.NotFound404;
        } else {
            statusCode = HttpStatusCode.Ok200;
        }
        return new EndpointResponse(responseBytes, EndpointResponse.contentTypeText, statusCode);
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

    private static int getPortHttps() {

        RunMode runMode = RunMode.getRunMode();

        // Return the HTTPS web port.
        int genericPort = PreferencesUtil.getInt("web_port_https", 443);
        return PreferencesUtil.getInt("web_port_https_" + runMode.getOverrideSuffix(), genericPort);
    }
}
