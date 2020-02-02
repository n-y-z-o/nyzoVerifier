package co.nyzo.verifier.client;

import co.nyzo.verifier.web.EndpointResponse;
import co.nyzo.verifier.web.elements.HtmlElement;

import java.util.List;

public interface ExecutionResult {

    EndpointResponse toEndpointResponse();
    HtmlElement toHtml();
    void toConsole(CommandOutput output);
}
