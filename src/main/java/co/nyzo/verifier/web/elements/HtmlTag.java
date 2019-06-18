package co.nyzo.verifier.web.elements;

import co.nyzo.verifier.web.WebUtil;

import java.nio.charset.StandardCharsets;
import java.util.*;

public abstract class HtmlTag implements HtmlElement {

    private List<HtmlElement> elements = new ArrayList<>();
    private Map<String, String> attributes = new HashMap<>();

    public abstract String getName();

    public HtmlElement add(HtmlElement element) {
        elements.add(element);
        return element;
    }

    public HtmlTag attr(String name, String value) {
        attributes.put(name, value);
        return this;
    }

    public HtmlTag addRaw(String rawHtml) {
        elements.add(new RawHtml(rawHtml));
        return this;
    }

    @Override
    public String render() {
        StringBuilder result = new StringBuilder();
        result.append("<").append(getName());
        for (String name : attributes.keySet()) {
            result.append(" ").append(name).append("=\"").append(attributes.get(name)).append("\"");
        }
        result.append(">");
        for (HtmlElement element : elements) {
            result.append(element.render());
        }
        result.append("</").append(getName()).append(">");

        return result.toString();
    }

    public byte[] renderByteArray() {

        return render().getBytes(StandardCharsets.UTF_8);
    }

    public Script ajaxUpdate(String endpoint, long interval) {

        StringBuilder script = new StringBuilder();

        // To allow the script to work properly, ensure an ID has been set.
        String id = attributes.get("id");
        if (id == null) {
            id = WebUtil.nextId();
            attributes.put("id", id);
        }

        // Wrap the script in a function for local variable scoping.
        script.append("(function() {");

        // Store the current timestamp and the arguments. The timestamp is used to ensure the div does not contain
        // out-of-date content.
        script.append("var refreshTimestamp = Date.now();");
        script.append("var interval = ").append(interval).append(";");
        script.append("var id = '").append(id).append("';");
        script.append("var endpoint = '").append(endpoint).append("';");

        // This is a simple Ajax update performed at the specified interval (ms). The indentation is for readability in
        // this code, not formatting in the rendered page.
        script.append("setInterval(function() {");
        script.append("  var requestTimestamp = Date.now();");
        script.append("  var refreshRequest = new XMLHttpRequest();");
        script.append("  refreshRequest.onreadystatechange = function() {");
        script.append("    if (this.readyState == 4 && this.status == 200) {");
        script.append("      document.getElementById(id).innerHTML = this.responseText;");
        script.append("      refreshTimestamp = requestTimestamp;");
        script.append("      if (refreshTimestamp >= Date.now() - 3 * interval) {");
        script.append("        document.getElementById(id).style.opacity = 1.0;");
        script.append("      }");
        script.append("    }");
        script.append("  };");
        script.append("  refreshRequest.open('GET', endpoint, true);");
        script.append("  refreshRequest.send();");
        script.append("}, interval);");

        // Set a second periodic function to indicate that the content is stale if its age is more than 3 times the
        // specified interval. The update is at 2 times the refresh rate, so the visual indication will trigger at
        // 3 to 3.5 times the interval.
        script.append("setInterval(function() {");
        script.append("  if (refreshTimestamp < Date.now() - 3 * interval) {");
        script.append("    document.getElementById(id).style.opacity = 0.5;");
        script.append("  }");
        script.append("}, interval / 2);");

        // Close the function that wraps the script.
        script.append("})();");

        return new Script(script.toString());
    }
}
