package co.nyzo.verifier.web;

import co.nyzo.verifier.Block;
import co.nyzo.verifier.BlockManager;
import co.nyzo.verifier.web.elements.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class CycleController {

    public static final String pageEndpoint = "/cycle";
    public static final String updateEndpoint = "/cycleUpdate";

    private static RawHtml cycleElement = null;
    private static final AtomicLong cycleElementHeight = new AtomicLong(-1L);

    public static EndpointResponse page(Map<String, String> queryParameters, byte[] sourceIpAddress) {

        // Make the HTML page.
        Html html = new Html();

        // Add the head and body to the page.
        Head head = (Head) html.add(new Head());
        Body body = (Body) html.add(new Body().attr("style", "font-family: sans-serif; text-align: center"));

        // Add the div cycle container and the cycle content.
        Div cycleDiv = (Div) body.add(new Div().attr("id", "cycleDiv"));
        cycleDiv.add(divContent());

        // Add the Ajax update for the cycle div to the head of the document.
        head.add(cycleDiv.ajaxUpdate(updateEndpoint, 5000));

        return new EndpointResponse(html.renderByteArray());
    }

    private static HtmlElement divContent() {

        // Make an HTML element list to hold the result.
        HtmlElementList result = new HtmlElementList();

        // Add the frozen-edge information and cycle.
        Block frozenEdge = BlockManager.getFrozenEdge();
        if (frozenEdge == null) {
            result.add(new H3("Frozen edge not yet available"));
        } else {
            result.add(new H3("Frozen edge: " + BlockManager.getFrozenEdgeHeight()));
            int blocksFromOpen = (int) (BlockManager.openEdgeHeight(false) - frozenEdge.getBlockHeight());
            result.add(new H3("(" + WebUtil.sanitizedNickname(frozenEdge.getVerifierIdentifier()) + ", " +
                    blocksFromOpen + " behind open edge)"));

            // Add the cycle. This check/generate procedure is not strictly thread-safe, but in the worst case, it will
            // result in a small amount of extra work, not incorrect behavior.
            if (frozenEdge.getBlockHeight() > cycleElementHeight.get()) {
                cycleElement = generateCycleElement();
                cycleElementHeight.set(frozenEdge.getBlockHeight());
            }
            result.add(cycleElement);
        }

        return result;
    }

    private static RawHtml generateCycleElement() {

        StringBuilder result = new StringBuilder("<div>");
        String separator = "";
        for (ByteBuffer identifier : BlockManager.verifiersInCurrentCycleList()) {
            result.append(separator).append(WebUtil.sanitizedNickname(identifier.array()));
            separator = " &rarr; ";
        }
        result.append("</div>");

        return new RawHtml(result.toString());
    }

    public static EndpointResponse update(Map<String, String> queryParameters, byte[] sourceIpAddress) {

        return new EndpointResponse(divContent().render().getBytes(StandardCharsets.UTF_8));
    }
}
