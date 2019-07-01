package co.nyzo.verifier.micropay;

import co.nyzo.verifier.Transaction;
import co.nyzo.verifier.client.ClientTransactionUtil;
import co.nyzo.verifier.nyzoString.*;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PreferencesUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.web.EndpointMethod;
import co.nyzo.verifier.web.EndpointResponse;
import co.nyzo.verifier.web.WebUtil;
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
    public static final String clientPingEndpoint = "/micropay/ping";
    public static final String clientAuthorizationEndpoint = "/micropay/authorize";
    public static final String pingResponse = "hello, Micropay user!";

    public static Map<String, EndpointMethod> buildEndpointMap() {

        Map<String, EndpointMethod> map = new ConcurrentHashMap<>();

        File rootFile = new File(dataRootDirectory);
        File[] files = rootFile.listFiles();
        process(files, rootFile.getAbsolutePath(), map);

        return map;
    }

    private static void process(File[] files, String rootFilePath, Map<String, EndpointMethod> map) {

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
                        map.put(path, new MicropayEndpoint(path, file));
                    }
                }
            }
        }
    }

    public static EndpointResponse clientPingPage(Map<String, String> queryParameters, byte[] sourceIpAddress) {
        EndpointResponse response = new EndpointResponse((pingResponse + " " + IpUtil.addressAsString(sourceIpAddress))
                .getBytes(StandardCharsets.UTF_8));
        response.setHeader("Access-Control-Allow-Origin", "*");
        return response;
    }

    public static EndpointResponse clientAuthorizationPage(Map<String, String> queryParameters, byte[] sourceIpAddress) {

        // Make the HTML page.
        Html html = new Html();

        // Add the head and body to the page.
        Head head = (Head) html.add(new Head().attr("lang", "en"));
        head.add(new Title("Nyzo Micropay authorization"));
        Body body = (Body) html.add(new Body().attr("style", "font-family: sans-serif; text-align: center"));

        // Get the Micropay string.
        String micropayParameter = queryParameters.getOrDefault("micropay", "").trim();
        NyzoStringTransaction transactionString = null;
        if (micropayParameter.isEmpty()) {
            body.add(new P("no Micropay string provided"));
        } else {
            NyzoString decodedString = NyzoStringEncoder.decode(micropayParameter);
            if (decodedString instanceof NyzoStringMicropay) {

                // Add the header and Micropay amount and receiver.
                body.add(new H1("Nyzo Micropay"));
                NyzoStringMicropay micropay = (NyzoStringMicropay) decodedString;
                body.add(new P("amount: " + PrintUtil.printAmount(micropay.getAmount())).attr("style",
                        "font-size: larger; font-weight: bold;"));
                body.add(new P("receiver ID: " +
                        NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(micropay.getReceiverIdentifier())))
                        .attr("style", "word-break: break-all;"));

                // Add the sender data.
                String senderData =
                        WebUtil.sanitizeString(ClientTransactionUtil.senderDataString(micropay.getSenderData()).trim());
                if (senderData.isEmpty()) {
                    senderData = "<span style=\"font-style: italic\">empty</span>";
                }
                body.add(new P("sender data: " + senderData));

                // If Micropay is configured correctly and the transaction amount is less than the Micropay maximum,
                // create the transaction. Otherwise, display the issue.
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
        String callback = queryParameters.getOrDefault("callback", "");
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

            // The "cancel" link is just the raw callback.
            body.add(new A().attr("href", callback).addRaw("cancel").attr("style", WebUtil.cancelButtonStyle +
                    " display: table;"));
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
