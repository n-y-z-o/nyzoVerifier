package co.nyzo.verifier.web;

import co.nyzo.verifier.Block;
import co.nyzo.verifier.BlockManager;
import co.nyzo.verifier.Version;
import co.nyzo.verifier.sentinel.ManagedVerifier;
import co.nyzo.verifier.sentinel.Sentinel;
import co.nyzo.verifier.web.elements.*;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

public class SentinelController {

    public static final String pageEndpoint = "/";
    public static final String updateEndpoint = "/update";

    public static EndpointResponse page(Map<String, String> queryParameters, byte[] sourceIpAddress) {

        // Make the HTML page.
        Html html = new Html();

        // Add the head and body to the page.
        Head head = (Head) html.add(new Head());
        Body body = (Body) html.add(new Body().attr("style", "font-family: sans-serif; text-align: center"));

        // Add standard metadata to the head.
        head.addStandardMetadata();

        // Add the container div to the page and content to the container div.
        Div container = (Div) body.add(new Div().attr("id", "container").attr("style", "display: inline-block;"));
        container.add(divContent());

        // Add the Ajax update for the container div to the head of the document.
        head.add(container.ajaxUpdate(updateEndpoint, 5000));

        // Add the version to the bottom of the page.
        body.add(new P("Nyzo sentinel, version " + Version.getVersion()).attr("style", "font-style: italic;"));

        return new EndpointResponse(html.renderByteArray());
    }

    public static EndpointResponse update(Map<String, String> queryParameters, byte[] sourceIpAddress) {
        return new EndpointResponse(divContent().render().getBytes(StandardCharsets.UTF_8));
    }

    private static HtmlElement divContent() {
        HtmlElementList content = new HtmlElementList();
        content.add(header());
        content.add(verifierTable());
        return content;
    }

    private static HtmlElement header() {

        // Add the frozen-edge information.
        Block frozenEdge = BlockManager.getFrozenEdge();
        StringBuilder header = new StringBuilder();
        if (frozenEdge == null) {
            header.append("Frozen edge not yet available");
        } else {
            header.append("Frozen edge: ").append(BlockManager.getFrozenEdgeHeight());
            int blocksFromOpen = (int) (BlockManager.openEdgeHeight(false) - frozenEdge.getBlockHeight());
            header.append("<br>(").append(WebUtil.sanitizedNickname(frozenEdge.getVerifierIdentifier())).append(", ")
                    .append(blocksFromOpen).append(" behind open edge)");
        }

        // Add the efficiency rating.
        header.append(String.format("<br>Efficiency: %.1f%%", Sentinel.getEfficiency()));

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
                ".verifier-tile-container { width: " + tileContainerWidth + "; min-width: " + tileContainerWidth +
                "; height: 0; display: table-caption; }" +
                ".verifier-tile { display: inline-block; width: 1.3rem; height: 2.1rem; }" +
                ".verifier-tile-line { position: relative; left: 0; width: 1.3rem; height: 0.2rem; " +
                "background-color: #0005; }" +
                ".verifier-tile-label { color: white; width: 1.3rem; position: relative; display: table-cell; " +
                "padding-top: 0.3rem; font-weight: bold; }" +
                ".separator-row { height: 1px; }"));

        // Build the table.
        Collection<ManagedVerifier> managedVerifiers = Sentinel.getManagedVerifiers();
        if (managedVerifiers.isEmpty()) {
            div.add(new H3("Managed verifiers not yet available"));
        }
        for (ManagedVerifier verifier : managedVerifiers) {

            // Get the index and array for recent queries.
            int queryIndex = verifier.getQueryIndex();
            int[] results = verifier.getQueryResults();

            // Build the div of color-coded tiles for the recent results, counting the results in the process.
            int countNotYetQueried = 0;
            int countError = 0;
            int countEmpty = 0;
            int countReceivedBlock = 0;
            Div tileContainer = (Div) new Div().attr("class", "verifier-tile-container");
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
                Div tile = (Div) tileContainer.add(new Div().attr("class", "verifier-tile")
                        .attr("style", "background-color: " + color));

                // This is a simple line over the cell to show motion.
                double position = Math.abs(arrayIndex * 2.0 / results.length - 1.0) * 1.9;
                tile.add(new Div().attr("class", "verifier-tile-line").attr("style",
                        String.format("top: %.1frem;", position)));

                // This is the number label.
                tile.add(new Div().attr("class", "verifier-tile-label").addRaw(label));
            }

            Div row = (Div) div.add(new Div().attr("class", "verifier-row"));
            String nickname = WebUtil.sanitizedNickname(verifier.getIdentifier());
            String className = "verifier-label" + (verifier.isQueriedLastInterval() ? " verifier-label-active" : "");
            row.add(new Div().attr("class", className).addRaw(nickname));
            row.add(tileContainer);

            div.add(new Div().attr("class", "separator-row"));
        }

        return div;
    }
}
