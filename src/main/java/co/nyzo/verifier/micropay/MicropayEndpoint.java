package co.nyzo.verifier.micropay;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.ClientDataManager;
import co.nyzo.verifier.client.ClientTransactionUtil;
import co.nyzo.verifier.nyzoString.NyzoString;
import co.nyzo.verifier.nyzoString.NyzoStringEncoder;
import co.nyzo.verifier.nyzoString.NyzoStringMicropay;
import co.nyzo.verifier.nyzoString.NyzoStringTransaction;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.web.EndpointMethod;
import co.nyzo.verifier.web.EndpointResponse;
import co.nyzo.verifier.web.WebUtil;
import co.nyzo.verifier.web.elements.*;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MicropayEndpoint implements EndpointMethod {

    // This is a map from IP address to authorization. This is a minimal implementation with no persistence. This will
    // likely evolve into a more interesting implementation with persistence/recovery from blockchain data.
    private final Map<ByteBuffer, MicropayAuthorization> authorizations = new ConcurrentHashMap<>();
    private final AtomicInteger authorizationsAddedSinceCleanup = new AtomicInteger(0);

    private String path;
    private long amount;
    private File file;

    public MicropayEndpoint(String path, File file) {
        this.path = path;
        this.amount = getMicropayAmount(file);
        this.file = file;
    }

    public String getPath() {
        return path;
    }

    public long getAmount() {
        return amount;
    }

    private static long getMicropayAmount(File file) {

        long amount = 0L;
        try {
            String fileContents = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())),
                    StandardCharsets.UTF_8);
            fileContents = fileContents.replaceAll("\\s+", " ");
            String[] split = fileContents.split("<?meta ");
            for (int i = 1; i < split.length; i++) {
                int closeIndex = split[i].indexOf('>');
                if (closeIndex > 0) {
                    String tag = split[i].substring(0, closeIndex).replaceAll("\\s+", "");
                    if (tag.contains("name=\"nyzo-micropay-amount\"")) {
                        tag = tag.replace("name=\"nyzo-micropay-amount\"", "");
                        tag = tag.replace("content=\"", "").replace("\"", "");
                        amount = (long) (Double.parseDouble(tag) * Transaction.micronyzoMultiplierRatio);
                    }
                }
            }
        } catch (Exception ignored) { }

        return amount;
    }

    @Override
    public EndpointResponse renderByteArray(Map<String, String> queryParameters, byte[] sourceIpAddress) {

        boolean authorized = amount == 0;

        // If not automatically authorized, look for a previous authorization.
        if (!authorized) {
            MicropayAuthorization authorization = authorizations.get(ByteBuffer.wrap(sourceIpAddress));
            authorized = authorization != null && authorization.isValid();
        }

        // If not yet authorized, look for a transaction. Note that this does not look for a transaction if a previous
        // authorization exists. If a user accidentally pays for content that we can deliver with a previous
        // authorization, charging for that content again would be unfair.
        byte[] result = null;
        StringBuilder transactionProblem = new StringBuilder();
        if (!authorized) {
            String transactionParameter = queryParameters.get("tx");
            if (transactionParameter != null && processTransactionParameter(transactionParameter, transactionProblem,
                    sourceIpAddress)) {
                // Authorize this view and store the authorization in the map. Periodically remove old authorizations
                // from the map.
                authorized = true;
                MicropayAuthorization authorization = new MicropayAuthorization();
                authorizations.put(ByteBuffer.wrap(sourceIpAddress), authorization);
                if (authorizationsAddedSinceCleanup.incrementAndGet() > 1000) {
                    authorizationsAddedSinceCleanup.set(0);
                    for (ByteBuffer mapIpAddress : new HashSet<>(authorizations.keySet())) {
                        MicropayAuthorization mapAuthorization = authorizations.get(mapIpAddress);
                        if (mapAuthorization != null && !mapAuthorization.isValid()) {
                            authorizations.remove(mapIpAddress);
                        }
                    }
                }
            }
        }

        String contentType = EndpointResponse.contentTypeDefault;
        if (authorized) {
            // If authorized, return the content.
            try {
                result = Files.readAllBytes(Paths.get(file.getAbsolutePath()));
                if (file.getName().toLowerCase().endsWith(".css")) {
                    contentType = EndpointResponse.contentTypeCss;
                }
            } catch (Exception ignored) { }

            if (result == null) {
                result = "unexpected error reading file".getBytes(StandardCharsets.UTF_8);
            }
        } else {
            // Otherwise, present the authorization page.
            result = serverAuthorizationPage(transactionProblem.toString(), sourceIpAddress);
        }

        EndpointResponse response = new EndpointResponse(result);
        response.setHeader("Content-type", contentType);
        return response;
    }

    private byte[] serverAuthorizationPage(String transactionProblem, byte[] sourceIpAddress) {

        // Make the HTML page.
        Html html = new Html();

        // Add the head and body to the page.
        Head head = (Head) html.add(new Head().attr("lang", "en"));
        head.add(new Title("Nyzo Micropay authorization"));
        Body body = (Body) html.add(new Body().attr("style", "font-family: sans-serif; text-align: center"));

        byte[] receiverIdentifier = MicropayServer.receiverIdentifier;
        byte[] senderData = senderDataForIp(sourceIpAddress);
        long timestamp = ClientTransactionUtil.suggestedTransactionTimestamp() + 1000L * 60L;  // 1-minute buffer
        Block block = BlockManager.frozenBlockForHeight(0L);
        NyzoStringMicropay micropayString = new NyzoStringMicropay(receiverIdentifier, senderData, amount, timestamp,
                block.getBlockHeight(), block.getHash());

        // Add the header.
        body.add(new H3("Nyzo Micropay premium content"));

        // Add the problem with a previous transaction, if present.
        if (transactionProblem != null && !transactionProblem.isEmpty()) {
            body.add(new H3(transactionProblem).attr("style", "color: red;"));
        }

        // Add the authorization link.
        body.add(new A().attr("id", "authorizeLink").attr("style", WebUtil.acceptButtonStyle + " opacity: 0.4;")
                .addRaw(PrintUtil.printAmount(amount)));

        // Add the error message. This is only displayed if the following script is unable to find a client.
        body.add(new P().attr("id", "errorMessage").addRaw("Unable to determine Nyzo Micropay client port")
                .attr("style", "color: red; display: none;"));

        // Add a simple script that checks the most common ports and activates the link on the appropriate port.
        String authorizationEndpoint = MicropayController.clientAuthorizationEndpoint + "?micropay=" +
                NyzoStringEncoder.encode(micropayString) + "&callback=" + MicropayServer.getCallbackBaseUrl() + path;
        String pingEndpoint = MicropayController.clientPingEndpoint;
        String pingResponse = MicropayController.pingResponse;
        body.add(new Script("" +
                "var successful = false;" +
                "function sendPing(port, protocol) {" +
                "  var ping = new XMLHttpRequest();" +
                "  ping.onload = function() {" +

                "    if (this.responseText.startsWith('" + pingResponse + "')) {" +
                "      successful = true;" +
                "      var link = document.getElementById('authorizeLink');" +
                "      link.href = protocol + '://127.0.0.1' + port + '" + authorizationEndpoint + "';\n" +
                "      link.style.opacity = 1.0;\n" +
                "      document.getElementById('errorMessage').style.display = 'none';\n" +
                "    }" +
                "  };" +

                "  var endpoint = protocol + '://127.0.0.1' + port + '/micropay/ping';" +
                "  ping.open('GET', endpoint, true);" +
                "  ping.send();" +
                "}" +

                "function sendPings() {" +
                "  var ports = [ '', ':8080', '', ':8443' ];" +
                "  var protocols = [ 'http', 'http', 'https', 'https' ];" +
                "  for (var i = 0; i < 4; i++) {" +
                "    sendPing(ports[i], protocols[i]);" +
                "  }" +
                "}" +
                "sendPings();" +

                "var interval = setInterval(function() {" +
                "  if (successful) {\n" +
                "    document.getElementById('errorMessage').style.display = 'none';\n" +
                "    clearInterval(interval);" +
                "  } else {\n" +
                "    document.getElementById('errorMessage').style.display = 'block';" +
                "    sendPings();" +
                "  }\n" +
                "}, 3000);"));

        return html.renderByteArray();
    }

    private boolean processTransactionParameter(String transactionParameter, StringBuilder transactionProblem,
                                                byte[] sourceIpAddress) {

        // Determine whether the transaction parameters are correct and whether it is likely to be accepted.
        boolean likelyToBeAccepted = false;
        NyzoString transactionString = NyzoStringEncoder.decode(transactionParameter);
        Transaction transaction = null;
        if (transactionString instanceof NyzoStringTransaction) {
            // Only proceed if the transaction is not null and the receiver, sender data, and amount are correct.
            transaction = ((NyzoStringTransaction) transactionString).getTransaction();
            if (transaction != null &&
                    ByteUtil.arraysAreEqual(transaction.getReceiverIdentifier(), Verifier.getIdentifier()) &&
                    ByteUtil.arraysAreEqual(transaction.getSenderData(), senderDataForIp(sourceIpAddress)) &&
                    transaction.getAmount() == amount) {

                // Perform initial validation of the transaction. If either an error or warning are produced, reject
                // the transaction.
                StringBuilder validationError = new StringBuilder();
                StringBuilder validationWarning = new StringBuilder();
                if (transaction.performInitialValidation(validationError, validationWarning)) {
                    likelyToBeAccepted = validationError.length() == 0 && validationWarning.length() == 0;
                }

                // Add the errors to the problem string builder to communicate them back to the user.
                transactionProblem.append(validationError);
                if (validationError.length() > 0 && validationWarning.length() > 0) {
                    transactionProblem.append(" ");
                }
                transactionProblem.append(validationWarning);
            } else if (transaction != null) {
                if (!ByteUtil.arraysAreEqual(transaction.getReceiverIdentifier(), Verifier.getIdentifier())) {
                    transactionProblem.append("The provided transaction has an incorrect recipient.");
                }
                if (!ByteUtil.arraysAreEqual(transaction.getSenderData(), senderDataForIp(sourceIpAddress))) {
                    if (transactionProblem.length() > 0) {
                        transactionProblem.append(" ");
                    }
                    transactionProblem.append("The provided transaction has incorrect sender data.");
                }
                if (transaction.getAmount() != amount) {
                    if (transactionProblem.length() > 0) {
                        transactionProblem.append(" ");
                    }
                    transactionProblem.append("The provided transaction is for an incorrect amount.");
                }
            }
        }

        // If the transaction is likely to be accepted, send it to the cycle. Otherwise, provide minimal feedback if
        // the feedback is currently empty.
        if (likelyToBeAccepted) {
            ClientTransactionUtil.sendTransactionToExpectedBlockVerifier(transaction, false);
        } else if (transactionProblem.length() == 0) {
            transactionProblem.append("There was an unspecified issue with the transaction provided.");
        }

        return likelyToBeAccepted;
    }

    private byte[] senderDataForIp(byte[] ipAddress) {
        // There are a lot of things that could be used in the sender data to tie the transaction to a user and path.
        // This one is simple: the verifier signs the IP and path, taking the first 32 bytes of the signature. Users'
        // IP addresses and paths accessed are not publicly exposed, but they can be verified easily by this server.

        byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
        byte[] combinedArray = new byte[ipAddress.length + pathBytes.length];
        System.arraycopy(ipAddress, 0, combinedArray, 0, ipAddress.length);
        System.arraycopy(pathBytes, 0, combinedArray, ipAddress.length, pathBytes.length);

        byte[] signature = Verifier.sign(combinedArray);
        return Arrays.copyOf(signature, FieldByteSize.maximumSenderDataLength);
    }

    @Override
    public String toString() {
        return "[MicropayEndpoint: path=" + getPath() + ", amount=" + PrintUtil.printAmount(getAmount()) + "]";
    }
}
