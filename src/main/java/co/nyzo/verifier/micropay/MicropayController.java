package co.nyzo.verifier.micropay;

import co.nyzo.verifier.Transaction;
import co.nyzo.verifier.nyzoString.*;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PreferencesUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.web.*;
import co.nyzo.verifier.web.elements.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MicropayController {

    private static final String senderSeedKey = "micropay_sender_key";
    private static final String maximumAmountKey = "micropay_max_amount_nyzos";
    private static final String dataRootKey = "micropay_data_root";

    private static final String dataRootDirectory = PreferencesUtil.get(dataRootKey);
    public static final Endpoint clientPingEndpoint = new Endpoint("/micropay/ping");
    public static final Endpoint serverPingEndpoint = new Endpoint("/micropay/ping");
    public static final Endpoint clientAuthorizationEndpoint = new Endpoint("/micropay/authorize");
    public static final String clientPingResponse = "hello, Micropay user!";

    public static Map<Endpoint, EndpointResponseProvider> buildEndpointMap() {

        Map<Endpoint, EndpointResponseProvider> map = new ConcurrentHashMap<>();

        File rootFile = new File(dataRootDirectory);
        File[] files = rootFile.listFiles();
        process(files, rootFile.getAbsolutePath(), map);

        return map;
    }

    private static void process(File[] files, String rootFilePath, Map<Endpoint, EndpointResponseProvider> map) {

        if (files != null) {
            for (File file : files) {

                // Do not process files that start with '.' or end with '~'. These are common hidden files. Also, do not
                // process files marked as hidden.
                String filename = file.getName();
                if (!file.isHidden() && !filename.startsWith(".") && !filename.endsWith("~")) {

                    // Recurse into subdirectories. Process files.
                    if (file.isDirectory()) {
                        process(file.listFiles(), rootFilePath, map);
                    } else {
                        // Remove the root and replace backslashes with forward slashes.
                        String path = file.getAbsolutePath().replace(rootFilePath, "");
                        path = path.replace('\\', '/');

                        // If the path ends in ".html", trim it.
                        if (path.toLowerCase().endsWith(".html")) {
                            path = path.substring(0, path.length() - 5);
                        }

                        // If the path ends in "/index", trim it.
                        if (path.toLowerCase().endsWith("/index")) {
                            path = path.substring(0, path.length() - 6);
                        }

                        // Ensure that the path starts with "/".
                        if (!path.startsWith("/")) {
                            path = "/" + path;
                        }

                        MicropayEndpoint endpoint = new MicropayEndpoint(path, file);
                        System.out.println("endpoint: " + endpoint);
                        map.put(new Endpoint(path), new MicropayEndpoint(path, file));
                    }
                }
            }
        }
    }

    public static EndpointResponse clientPingPage(EndpointRequest request) {
        EndpointResponse response = new EndpointResponse((clientPingResponse + " " +
                IpUtil.addressAsString(request.getSourceIpAddress())).getBytes(StandardCharsets.UTF_8));
        response.setHeader("Access-Control-Allow-Origin", "*");
        return response;
    }

    public static EndpointResponse serverPingPage(EndpointRequest request) {
        return new EndpointResponse(("hello, " + IpUtil.addressAsString(request.getSourceIpAddress()))
                .getBytes(StandardCharsets.UTF_8));
    }

    public static EndpointResponse clientAuthorizationPage(EndpointRequest request) {

        // Make the HTML page.
        Html html = (Html) new Html().attr("lang", "en");

        // Add the head and body to the page.
        Head head = (Head) html.add(new Head().addStandardMetadata());
        head.add(new Title("Nyzo Micropay authorization"));
        Body body = (Body) html.add(new Body().attr("style", "font-family: sans-serif; text-align: center"));

        // Get the Micropay string.
        String micropayParameter = request.getQueryParameters().getOrDefault("micropay", "").trim();
        NyzoStringTransaction transactionString = null;
        if (micropayParameter.isEmpty()) {
            body.add(new P("no Micropay string provided"));
        } else {
            NyzoString decodedString = NyzoStringEncoder.decode(micropayParameter);
            if (decodedString instanceof NyzoStringMicropay) {

                // Add the header and Micropay amount and receiver.
                body.add(new H1("Nyzo Micropay"));

                // If Micropay is configured correctly and the transaction amount is less than the Micropay maximum,
                // create the transaction. Otherwise, display the issue.
                NyzoStringMicropay micropay = (NyzoStringMicropay) decodedString;
                String error = "";
                NyzoStringPrivateSeed senderSeed = getSenderSeed();
                long maximumAmount = getMaximumTransactionAmountMicronyzos();
                if (getSenderSeed() == null) {
                    error = "Please set a valid Micropay sender key in preferences (" + senderSeedKey + ")";
                } else if (maximumAmount < 1) {
                    error = "Please set a valid Micropay maximum amount in preferences (" + maximumAmountKey + ")";
                } else if (micropay.getAmount() > maximumAmount) {
                    error = "amount " + PrintUtil.printAmount(micropay.getAmount()) + " exceeds Micropay maximum " +
                            PrintUtil.printAmount(maximumAmount);
                } else {
                    Transaction transaction = Transaction.standardTransaction(micropay.getTimestamp(),
                            micropay.getAmount(), micropay.getReceiverIdentifier(), micropay.getPreviousHashHeight(),
                            micropay.getPreviousBlockHash(), micropay.getSenderData(), senderSeed.getSeed());
                    transactionString = new NyzoStringTransaction(transaction);
                }

                if (!error.isEmpty()) {
                    body.add(new P(error).attr("style", "color: red;"));
                }
            } else {
                body.add(new P("invalid Micropay string: " + micropayParameter));
            }
        }

        // Get the callback URL and add the appropriate buttons.
        String callback = request.getQueryParameters().getOrDefault("callback", "");
        if (callback.isEmpty()) {
            body.add(new P("no callback provided"));
        } else {
            // If the transaction was produced, add the "approve" link.
            if (transactionString != null) {
                String link = callback + "?tx=" + NyzoStringEncoder.encode(transactionString);
                long amount = transactionString.getTransaction().getAmount();
                body.add(new A().attr("href", link).addRaw("approve " + PrintUtil.printAmount(amount)).attr("style",
                        WebUtil.acceptButtonStyle + " display: table; margin-bottom: 0.5rem;"));
            }

            // The "cancel" link has a "cancel=y" parameter instead of a transaction parameter.
            body.add(new A().attr("href", callback + "?cancel=y").addRaw("cancel").attr("style",
                    WebUtil.cancelButtonStyle + " display: table;"));
        }

        return new EndpointResponse(html.renderByteArray());
    }

    private static long getMaximumTransactionAmountMicronyzos() {
        return (long) (PreferencesUtil.getDouble(maximumAmountKey, 0) * Transaction.micronyzoMultiplierRatio);
    }

    private static NyzoStringPrivateSeed getSenderSeed() {

        NyzoString micropaySenderSeed  = NyzoStringEncoder.decode(PreferencesUtil.get(senderSeedKey));
        NyzoStringPrivateSeed senderSeed = null;
        if (micropaySenderSeed instanceof NyzoStringPrivateSeed) {
            senderSeed = ((NyzoStringPrivateSeed) micropaySenderSeed);
        }

        return senderSeed;
    }
}
