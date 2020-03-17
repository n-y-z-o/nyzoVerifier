package co.nyzo.verifier.web;

import co.nyzo.verifier.Block;
import co.nyzo.verifier.BlockManager;
import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.Version;
import co.nyzo.verifier.sentinel.ManagedVerifier;
import co.nyzo.verifier.sentinel.Sentinel;
import co.nyzo.verifier.web.elements.*;

import java.nio.charset.StandardCharsets;
import java.util.Collection;

public class SentinelController {

    public static final Endpoint pageEndpoint = new Endpoint("/");
    public static final Endpoint updateEndpoint = new Endpoint("/update");

    public static EndpointResponse page(EndpointRequest request) {

        // Make the HTML page.
        Html html = (Html) new Html().attr("lang", "en");

        // Add the head and body to the page.
        Head head = (Head) html.add(new Head().addStandardMetadata());
        Body body = (Body) html.add(new Body().attr("style", "font-family: sans-serif; text-align: center"));

        // Add the container div to the page and content to the container div.
        Div container = (Div) body.add(new Div().attr("id", "container").attr("style", "display: inline-block;"));
        container.add(divContent());

        // Add the Ajax update for the container div to the head of the document.
        head.add(container.ajaxUpdate(updateEndpoint.getPath(), 5000));

        return new EndpointResponse(html.renderByteArray());
    }

    public static EndpointResponse update(EndpointRequest request) {
        return new EndpointResponse(divContent().render().getBytes(StandardCharsets.UTF_8));
    }

    private static HtmlElement divContent() {
        HtmlElementList content = new HtmlElementList();
        content.add(header());
        content.add(verifierTable());
        content.add(incorrectVerifierNotices());
        content.add(new P("Nyzo sentinel, version " + Version.getVersion()).attr("style", "font-style: italic;"));
        return content;
    }

    private static HtmlElement header() {

        // Add the frozen-edge information.
        Block frozenEdge = BlockManager.getFrozenEdge();
        StringBuilder header = new StringBuilder();
        if (frozenEdge == null) {
            header.append("Frozen edge not yet available");
        } else {
            header.append("Frozen edge: ").append(frozenEdge.getBlockHeight());
            int blocksFromOpen = (int) (BlockManager.openEdgeHeight(false) - frozenEdge.getBlockHeight());
            header.append("<br>(").append(WebUtil.sanitizedNickname(frozenEdge.getVerifierIdentifier())).append(", ")
                    .append(blocksFromOpen).append(" behind open edge)");
        }

        // Add the cycle length.
        header.append("<br>Current cycle: ").append(BlockManager.isCycleComplete() ? BlockManager.currentCycleLength() :
                "-");

        // Add the efficiency rating.
        header.append(String.format("<br>Efficiency: %.1f%%", Sentinel.getEfficiency()));

        // Add the height at which a block was last transmitted.
        long lastBlockTransmitted = Sentinel.getLastBlockTransmissionHeight();
        String lastBlockString = Sentinel.getLastBlockTransmissionString();
        if (lastBlockString == null || lastBlockString.isEmpty()) {
            lastBlockString = lastBlockTransmitted < 0 ? "-" : lastBlockTransmitted + "";
        }
        header.append("<br>Last block transmitted: ").append(lastBlockString);

        // Add the results of the last block transmission.
        String lastBlockTransmissionResults = Sentinel.getLastBlockTransmissionResults();
        header.append("<br>Transmission results: ").append(lastBlockTransmissionResults.isEmpty() ? "-" :
                lastBlockTransmissionResults);

        // Add whether the sentinel is actively protecting verifiers. If the sentinel is not yet calculating valid chain
        // scores, it is unable to protect verifiers. If the frozen edge was verified longer ago than the
        // verifier-removal interval (80 seconds), then the protection status is uncertain.
        header.append("<br>Protecting verifiers: ");
        if (!Sentinel.isCalculatingValidChainScores()) {
            header.append("<span style=\"color: #f00;\">no</span>");
        } else if (frozenEdge == null || frozenEdge.getVerificationTimestamp() < System.currentTimeMillis() - 80000L) {
            header.append("<span style=\"color: #f80;\">uncertain</span>");
        } else {
            header.append("<span style=\"color: #080;\">yes</span>");
        }

        // Return the header.
        return new H3(header.toString());
    }

    private static HtmlElement verifierTable() {

        // Create the div and add the styles that will be used.
        Div div = (Div) new Div().attr("style", "display: table; margin: auto;");
        String tileContainerWidth = String.format("%.1frem", 1.3 * ManagedVerifier.queryHistoryLength);
        div.add(new Style(".verifier-row { display: table-row; background-color: #ddd; }" +
                ".verifier-label { display: table-cell; padding: 0.5rem 1.0rem 0 1.0rem; vertical-align: top; " +
                "white-space: nowrap; height: 1.6rem; }" +
                ".verifier-label-active { background-color: #999; }" +
                ".verifier-label-incorrect-identifier { background-color: #f80; }" +
                ".verifier-tile { display: table-cell; width: 1.3rem; height: 2.1rem; }" +
                ".verifier-tile-line { position: relative; left: 0; width: 1.3rem; height: 0.2rem; " +
                "background-color: rgba(0,0,0,0.3); }" +
                ".verifier-tile-label { color: white; width: 1.3rem; position: relative; display: table-cell; " +
                "padding-top: 0.3rem; font-weight: bold; }" +
                ".separator-row { height: 1px; }" +
                ".incorrect-verifier-notice { color: #f80; font-style: italic; }"));

        // Build the table.
        Collection<ManagedVerifier> managedVerifiers = Sentinel.getManagedVerifiers();
        if (managedVerifiers.isEmpty()) {
            div.add(new H3("Managed verifiers not yet available"));
        }
        for (ManagedVerifier verifier : managedVerifiers) {

            // Get the index and array for recent queries.
            int queryIndex = verifier.getQueryIndex();
            int[] results = verifier.getQueryResults();

            Div row = (Div) div.add(new Div().attr("class", "verifier-row"));
            String nickname = WebUtil.sanitizedNickname(verifier.getIdentifier());
            String className = "verifier-label";
            if (!ByteUtil.isAllZeros(verifier.getResponseIdentifier()) &&
                    !ByteUtil.arraysAreEqual(verifier.getIdentifier(), verifier.getResponseIdentifier())) {
                className += " verifier-label-incorrect-identifier";
            } else if (verifier.isQueriedLastInterval()) {
                className += " verifier-label-active";
            }
            row.add(new Div().attr("class", className).addRaw(nickname));

            // Build the div of color-coded tiles for the recent results, counting the results in the process.
            int countNotYetQueried = 0;
            int countError = 0;
            int countEmpty = 0;
            int countReceivedBlock = 0;
            for (int i = 0; i < results.length; i++) {
                int arrayIndex = (queryIndex + results.length - i - 1) % results.length;
                int result = results[arrayIndex];
                String color;
                String label = "&nbsp;";
                if (result == ManagedVerifier.queryResultNotYetQueriedValue) {
                    countNotYetQueried++;
                    color = "#ccc;";
                } else if (result == ManagedVerifier.queryResultErrorValue) {
                    countError++;
                    color = "#f00;";
                } else if (result == 0) {
                    countEmpty++;
                    color = "#4b4;";
                    label = "0";
                } else {
                    countReceivedBlock++;
                    color = "#080;";
                    label = result + "";
                }
                Div tile = (Div) row.add(new Div().attr("class", "verifier-tile")
                        .attr("style", "background-color: " + color));

                // This is a simple line over the cell to show motion.
                double position = Math.abs(arrayIndex * 2.0 / results.length - 1.0) * 1.9;
                tile.add(new Div().attr("class", "verifier-tile-line").attr("style",
                        String.format("top: %.1frem;", position)));

                // This is the number label.
                tile.add(new Div().attr("class", "verifier-tile-label").addRaw(label));
            }

            div.add(new Div().attr("class", "separator-row"));
        }

        return div;
    }

    private static HtmlElement incorrectVerifierNotices() {
        HtmlElementList notices = new HtmlElementList();
        Collection<ManagedVerifier> managedVerifiers = Sentinel.getManagedVerifiers();
        for (ManagedVerifier verifier : managedVerifiers) {
            if (!ByteUtil.isAllZeros(verifier.getResponseIdentifier()) &&
                    !ByteUtil.arraysAreEqual(verifier.getIdentifier(), verifier.getResponseIdentifier())) {
                String nickname = WebUtil.sanitizedNickname(verifier.getIdentifier());
                notices.add(new P().attr("class", "incorrect-verifier-notice").addRaw(nickname + " identifer: " +
                        ByteUtil.arrayAsStringWithDashes(verifier.getIdentifier()) + ", response identifier: " +
                        ByteUtil.arrayAsStringWithDashes(verifier.getResponseIdentifier())));
            }
        }

        return notices;
    }
}
