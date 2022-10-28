package co.nyzo.verifier.documentation;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.ClientTransactionUtil;
import co.nyzo.verifier.json.Json;
import co.nyzo.verifier.json.JsonArray;
import co.nyzo.verifier.json.JsonObject;
import co.nyzo.verifier.nyzoString.NyzoString;
import co.nyzo.verifier.nyzoString.NyzoStringEncoder;
import co.nyzo.verifier.nyzoString.NyzoStringPublicIdentifier;
import co.nyzo.verifier.util.LogUtil;
import co.nyzo.verifier.util.NetworkUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.web.*;
import co.nyzo.verifier.web.elements.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

public class DocumentationEndpoint implements EndpointResponseProvider {

    private String path;
    private File file;
    private String title;
    private DocumentationEndpointType type;
    private DocumentationEndpoint parent;
    private long micropayPrice;
    private byte[] micropayReceiverIdentifier;
    private byte[] micropaySenderData;
    private List<DocumentationEndpoint> children;

    public DocumentationEndpoint(String path, File file) {
        this.path = processPath(path);
        this.file = file.isDirectory() ? new File(file, "index.html") : file;
        this.type = determineType(this.file);
        this.title = findTitle(this.file, this.type);
        this.children = new ArrayList<>();

        loadMicropayParameters(this.file);
    }

    public String getPath() {
        return path;
    }

    public File getFile() {
        return file;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public DocumentationEndpointType getType() {
        return type;
    }

    public DocumentationEndpoint getParent() {
        return parent;
    }

    public void setParent(DocumentationEndpoint parent) {
        this.parent = parent;
    }

    public void addChild(DocumentationEndpoint child) {
        children.add(child);
        child.setParent(this);
    }

    public int getNumberOfChildren() {
        return children.size();
    }

    public DocumentationEndpoint getChild(int index) {
        return index >= 0 && index < children.size() ? children.get(index) : null;
    }

    private static String processPath(String path) {

        // Split the path into components.
        String[] components = path.split("/");

        // Process and reassemble the components.
        StringBuilder reassembled = new StringBuilder();
        for (String component : components) {
            // Remove the ordering index, if present.
            int underscoreIndex = -2;
            for (int i = 0; i < component.length() - 1 && underscoreIndex == -2; i++) {
                if (component.charAt(i) == '_') {
                    underscoreIndex = i;
                } else if (component.charAt(i) < '0' || component.charAt(i) > '9') {
                    underscoreIndex = -1;
                }
            }
            if (underscoreIndex >= 0) {
                component = component.substring(underscoreIndex + 1);
            }

            // Add the component to the reassembled path.
            if (!component.isEmpty()) {
                reassembled.append("/").append(component);
            }
        }

        if (reassembled.length() == 0) {
            reassembled.append("/");
        }

        return reassembled.toString();
    }

    private static String findTitle(File file, DocumentationEndpointType type) {
        String title = null;
        if (!file.isDirectory() && type == DocumentationEndpointType.Html) {
            try {
                String fileContents = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())),
                        StandardCharsets.UTF_8);
                for (int i = 0; i < 4 && title == null; i++) {
                    String openTag = "<h" + (i + 1) + ">";
                    String closeTag = ("</h" + (i + 1) + ">");
                    int startIndex = fileContents.indexOf(openTag);
                    if (startIndex >= 0) {
                        int endIndex = fileContents.indexOf(closeTag, startIndex);
                        if (endIndex >= 0) {
                            title = fileContents.substring(startIndex + openTag.length(), endIndex);
                        }
                    }
                }
            } catch (Exception ignored) { }
        }

        // If the title is null, use the path to determine the title.
        if (title == null) {
            title = file.getName().toLowerCase().equals("index.html") ? file.getParentFile().getName() : file.getName();
            if (title.toLowerCase().endsWith(".html")) {
                title = title.substring(0, title.length() - 5);
            }
        }

        return title;
    }

    private void loadMicropayParameters(File file) {
        long price = 0L;
        byte[] receiverIdentifier = null;
        byte[] senderData = null;

        File micropayFile = new File(file.getAbsolutePath() + ".micropay");
        if (micropayFile.exists()) {
            try {
                List<String> contentsOfFile = Files.readAllLines(Paths.get(micropayFile.getAbsolutePath()));
                for (String line : contentsOfFile) {
                    try {
                        line = line.trim();
                        int indexOfHash = line.indexOf("#");
                        if (indexOfHash >= 0) {
                            line = line.substring(0, indexOfHash).trim();
                        }
                        int splitIndex = line.indexOf("=");
                        if (splitIndex > 0) {
                            String key = line.substring(0, splitIndex).trim().toLowerCase();
                            String value = line.substring(splitIndex + 1).trim();
                            if (key.equals("price")) {
                                price = (long) (Double.parseDouble(value) * Transaction.micronyzoMultiplierRatio);
                            } else if (key.equals("receiver_identifier")) {
                                NyzoString receiverString = NyzoStringEncoder.decode(value);
                                if (receiverString instanceof NyzoStringPublicIdentifier) {
                                    receiverIdentifier = ((NyzoStringPublicIdentifier) receiverString).getIdentifier();
                                }
                            } else if (key.equals("sender_data")) {
                                if (ClientTransactionUtil.isNormalizedSenderDataString(value)) {
                                    senderData = ClientTransactionUtil.bytesFromNormalizedSenderDataString(value);
                                } else {
                                    senderData = value.getBytes(StandardCharsets.UTF_8);
                                }
                            }
                        }
                    } catch (Exception ignored) { }
                }
            } catch (Exception ignored) { }
        }

        this.micropayPrice = price;
        this.micropayReceiverIdentifier = receiverIdentifier;
        this.micropaySenderData = senderData;
    }

    private static DocumentationEndpointType determineType(File file) {

        DocumentationEndpointType type;
        String filename = file.getName().toLowerCase();
        if (filename.endsWith(".css")) {
            type = DocumentationEndpointType.Css;
        } else if (filename.endsWith(".htm")) {
            type = DocumentationEndpointType.HtmlFragment;
        } else if (filename.endsWith(".ico")) {
            type = DocumentationEndpointType.Ico;
        } else if (filename.endsWith(".js")) {
            type = DocumentationEndpointType.JavaScript;
        } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            type = DocumentationEndpointType.Jpeg;
        } else if (filename.endsWith(".png")) {
            type = DocumentationEndpointType.Png;
        } else if (filename.endsWith(".txt")) {
            type = DocumentationEndpointType.Text;
        } else if (file.exists()) {
            type = DocumentationEndpointType.Html;
        } else {
            type = DocumentationEndpointType.Empty;
        }

        return type;
    }

    @Override
    public EndpointResponse getResponse(EndpointRequest request) {

        EndpointResponse result;
        StringBuilder micropayFailure = new StringBuilder();
        if (micropayAuthorized(request, micropayFailure)) {
            if (type == DocumentationEndpointType.Html) {
                result = getResponseForHtml();
            } else {
                result = getResponseForRaw();
            }
        } else {
            result = getResponseForInvalidMicropay(micropayFailure.toString());
        }

        return result;
    }

    private boolean micropayAuthorized(EndpointRequest request, StringBuilder failureCause) {

        // Set the flag initially to false. It will be set to true before returning if properly authorized.
        boolean authorized = false;
        if (micropayPrice > 0 && micropayReceiverIdentifier != null) {
            Map<String, String> queryParameters = request.getQueryParameters();
            String transaction = queryParameters.getOrDefault("transaction", "").trim();
            String supplementalTransaction = queryParameters.getOrDefault("supplementalTransaction", "").trim();
            if (transaction.isEmpty()) {
                failureCause.append("No transaction provided. ");
            }
            if (supplementalTransaction.isEmpty()) {
                failureCause.append("No supplemental transaction provided. ");
            }

            if (!transaction.isEmpty() && !supplementalTransaction.isEmpty()) {
                // Forward the transaction to the client.
                String clientFullUrl = "https://client.nyzo.co/api/forwardTransaction?transaction=" + transaction +
                        "&supplementalTransaction=" + supplementalTransaction;
                String clientResponse = NetworkUtil.stringForUrl(clientFullUrl, 1500);

                Object responseJson = Json.parse(clientResponse);

                // Drill into the response to get the appropriate object.
                JsonObject response = null;
                if (responseJson instanceof JsonObject) {
                    Object resultArray = ((JsonObject) responseJson).get("result");
                    if (resultArray instanceof JsonArray) {
                        Object responseObject = ((JsonArray) resultArray).get(0);
                        if (responseObject instanceof JsonObject) {
                            response = (JsonObject) responseObject;
                        }
                    }
                }

                if (response == null) {
                    failureCause.append("Response from client is null. ");
                } else {

                    // We want the purchaser of the Micropay content to be acting in good faith, making an attempt
                    // to provide appropriate payment for this content. Therefore, we want to see the following
                    // fulfilled.
                    // (1) The transaction was previously forwarded or is in the blockchain.
                    // (2) The sender balance is at least the sum of the minimum preferred balance and the
                    //     required transaction amount or the transaction is in the blockchain.
                    // (3) The supplemental transaction is valid.
                    // (4) The transaction amount is at least the required transaction amount.
                    // (5) The transaction receiver is correct.
                    // (6) The transaction sender data is correct.

                    boolean inBlockchain = response.getBoolean("inBlockchain", false);
                    long senderBalance = (long) (response.getDouble("senderBalance", 0.0) *
                            Transaction.micronyzoMultiplierRatio);
                    long amount = (long) (response.getDouble("amount", 0.0) * Transaction.micronyzoMultiplierRatio);
                    byte[] receiverIdentifier = ByteUtil.byteArrayFromHexString(response.getString("receiverIdBytes",
                            ""), FieldByteSize.identifier);
                    String senderDataString = response.getString("senderDataBytes", "").replace("-", "");
                    byte[] senderData = ByteUtil.byteArrayFromHexString(senderDataString,
                            senderDataString.length() / 2);

                    authorized = (response.getBoolean("previouslyForwarded", false) || inBlockchain) &&  // (1)
                            (senderBalance >= (BalanceManager.minimumPreferredBalance + micropayPrice) ||
                                    inBlockchain) &&  // (2)
                            response.getBoolean("supplementalTransactionValid", false) &&  // (3)
                            amount >= micropayPrice &&  // (4)
                            ByteUtil.arraysAreEqual(micropayReceiverIdentifier, receiverIdentifier) &&  // (5)
                            (micropaySenderData == null || micropaySenderData.length == 0 ||
                                    ByteUtil.arraysAreEqual(micropaySenderData, senderData));  // (6)

                    // If authorization failed, add information about why it failed to help users and developers fix the
                    // problem.
                    if (!authorized) {
                        // Condition 1.
                        if (!response.getBoolean("previouslyForwarded", false) && !inBlockchain) {
                            failureCause.append("Transaction was not previously forwarded and not in the blockchain. ");
                        }

                        // Condition 2.
                        if (senderBalance < (BalanceManager.minimumPreferredBalance + micropayPrice) && !inBlockchain) {
                            failureCause.append("Sender balance of ").append(PrintUtil.printAmount(senderBalance))
                                    .append(" is less than the sum of the minimum preferred balance (")
                                    .append(PrintUtil.printAmount(BalanceManager.minimumPreferredBalance))
                                    .append(") and the Micropay price (").append(PrintUtil.printAmount(micropayPrice))
                                    .append("), and the transaction is not in the blockchain. ");
                        }

                        // Condition 3.
                        if (!response.getBoolean("supplementalTransactionValid", false)) {
                            failureCause.append("The supplemental transaction is invalid. ");
                        }

                        // Condition 4.
                        if (amount < micropayPrice) {
                            failureCause.append("The transaction amount of ").append(PrintUtil.printAmount(amount))
                                    .append(" is less than the Micropay price of ")
                                    .append(PrintUtil.printAmount(micropayPrice)).append(". ");
                        }

                        // Condition 5.
                        if (!ByteUtil.arraysAreEqual(micropayReceiverIdentifier, receiverIdentifier)) {
                            failureCause.append("The transaction receiver identifier of ")
                                    .append(NyzoStringEncoder
                                            .encode(new NyzoStringPublicIdentifier(receiverIdentifier)))
                                    .append(" does not match the required Micropay receiver identifier of ")
                                    .append(NyzoStringEncoder
                                            .encode(new NyzoStringPublicIdentifier(micropayReceiverIdentifier)))
                                    .append(". ");
                        }

                        // Condition 6.
                        if (micropaySenderData != null && micropaySenderData.length != 0 &&
                                !ByteUtil.arraysAreEqual(micropaySenderData, senderData)) {
                            failureCause.append("The transaction sender data of ")
                                    .append(ClientTransactionUtil.senderDataForDisplay(senderData))
                                    .append(" does not match the required Micropay sender data of ")
                                    .append(ClientTransactionUtil.senderDataForDisplay(micropaySenderData))
                                    .append(". ");
                        }
                    }
                }
            }
        } else {
            // Authorization is not required by this endpoint, so authorization always succeeds.
            authorized = true;
        }

        return authorized;
    }

    public EndpointResponse getResponseForInvalidMicropay(String failureCause) {

        String receiverIdentifierString =
                NyzoStringEncoder.encode(new NyzoStringPublicIdentifier(micropayReceiverIdentifier));
        StringBuilder message = new StringBuilder("This is a Micropay resource. Please provide a valid payment of ")
                .append(PrintUtil.printAmount(micropayPrice)).append(" to ").append(receiverIdentifierString);
        if (micropaySenderData != null && micropaySenderData.length > 0) {
            message.append(" with sender data \"").append(WebUtil.sanitizedSenderDataForDisplay(micropaySenderData))
                    .append("\"");
        }
        message.append(" to access this content.");

        if (failureCause != null && !failureCause.trim().isEmpty()) {
            message.append(" Failure cause: ").append(failureCause);
        }

        return new EndpointResponse(message.toString().getBytes(StandardCharsets.UTF_8),
                EndpointResponse.contentTypeText, HttpStatusCode.PaymentRequired402);
    }

    public EndpointResponse getResponseForHtml() {

        // Make the HTML page.
        Html html = (Html) new Html().attr("lang", "en");

        // Add the head and body to the page.
        Head head = (Head) html.add(new Head().addStandardMetadata(title));
        Body body = (Body) html.add(new Body().attr("style", "font-family: sans-serif;"));

        // Get the contents of the file for this page.
        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(file.getAbsolutePath()));
        } catch (Exception ignored) {
            lines = new ArrayList<>();
        }

        // Add the hover button styles to the head.
        head.add(WebUtil.hoverButtonStyles);

        // Add the styles from the page.
        for (String line : lines) {
            line = getLink(line);
            if (!line.isEmpty()) {
                head.add(new RawHtml(line));
            }
        }

        // Add the breadcrumbs to the top of the page.
        if (!path.equals("/")) {
            List<DocumentationEndpoint> endpointPath = new ArrayList<>();
            DocumentationEndpoint endpoint = this;
            while (endpoint != null) {
                endpointPath.add(0, endpoint);
                endpoint = endpoint.getParent();
            }
            for (int i = 0; i < endpointPath.size(); i++) {
                if (i > 0) {
                    body.add(new RawHtml("&rarr;"));
                }
                body.add(new A().attr("class", "hover-button").attr("href", endpointPath.get(i).getPath())
                        .addRaw(endpointPath.get(i).getTitle()));
            }
            body.add(new Hr());
        }

        // Add the contents for the page.
        if (!lines.isEmpty()) {
            StringBuilder contents = new StringBuilder();
            String separator = "";
            for (String line : lines) {
                // Filter CSS links and add all non-empty lines.
                line = removeLink(line);
                if (!line.isEmpty()) {
                    contents.append(separator).append(line);
                    separator = "\n";
                }
            }
            body.add(new RawHtml(contents.toString()));
        }

        // Add buttons for all HTML children.
        for (DocumentationEndpoint child : children) {
            if (child.getType() == DocumentationEndpointType.Html) {
                body.add(new A().attr("class", "hover-button").attr("href", child.getPath()).addRaw(child.getTitle()));
            }
        }

        // If this it the root, add the version number.
        if (path.equals("/")) {
            body.add(new Hr().attr("style", "margin-top: 2rem;"));
            body.add(new P("Nyzo documentation server, version " + Version.getVersion()).attr("style",
                    "font-style: italic;"));
        }

        return new EndpointResponse(html.renderByteArray(), type.getContentType());
    }

    public EndpointResponse getResponseForRaw() {

        byte[] result = new byte[0];
        if (file.exists()) {
            try {
                result = Files.readAllBytes(Paths.get(file.getAbsolutePath()));
            } catch (Exception ignored) { }
        }

        return new EndpointResponse(result, type.getContentType());
    }

    private static String getLink(String htmlLine) {
        int startIndex = htmlLine.indexOf("<link ");
        int endIndex = startIndex < 0 ? -1 : htmlLine.indexOf(">", startIndex + 1);
        return startIndex >= 0 && endIndex > 0 ? htmlLine.substring(startIndex, endIndex + 1) : "";
    }

    private static String removeLink(String htmlLine) {

        int startIndex = htmlLine.indexOf("<link ");
        int endIndex = startIndex < 0 ? -1 : htmlLine.indexOf(">", startIndex + 1);

        String result;
        if (startIndex < 0 || endIndex < 0) {
            result = htmlLine;
        } else {
            result = "";
            if (startIndex > 0) {
                result += htmlLine.substring(0, startIndex);
            }
            if (endIndex < htmlLine.length() - 1) {
                result += htmlLine.substring(endIndex + 1);
            }
        }

        return result;
    }

    @Override
    public String toString() {
        return "[DocumentationEndpoint:" + path + "]";
    }
}
