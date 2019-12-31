package co.nyzo.verifier.client;

import co.nyzo.verifier.web.elements.HtmlElement;

import java.util.List;

public interface ExecutionResult {

    String toJson();
    HtmlElement toHtml();
    void toConsole(CommandOutput output);
}
