package co.nyzo.verifier.web.elements;

import co.nyzo.verifier.web.WebUtil;

import java.nio.charset.StandardCharsets;
import java.util.*;

public abstract class HtmlTag implements HtmlElement {

    private List<HtmlElement> elements = new ArrayList<>();
    private Map<String, String> attributes = new HashMap<>();
    private boolean includeClosingTag = true;

    public abstract String getName();

    public HtmlElement add(HtmlElement element) {
        if (element != null) {
            elements.add(element);
        }
        return element;
    }

    public HtmlTag attr(String name, String value) {
        attributes.put(name, value);
        return this;
    }

    public String getAttr(String name) {
        return attributes.get(name);
    }

    public HtmlTag addRaw(String rawHtml) {
        elements.add(new RawHtml(rawHtml));
        return this;
    }

    public void setIncludeClosingTag(boolean includeClosingTag) {
        this.includeClosingTag = includeClosingTag;
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
        if (includeClosingTag) {
            result.append("</").append(getName()).append(">");
        }

        return result.toString();
    }

    public byte[] renderByteArray() {

        return render().getBytes(StandardCharsets.UTF_8);
    }

    public Script ajaxUpdate(String endpoint, long refreshInterval) {
        return ajaxUpdate(new String[] { endpoint }, refreshInterval, refreshInterval * 3, false);
    }

    public Script ajaxUpdate(String[] endpoints, long refreshInterval, long staleInterval, boolean immediateRefresh) {

        StringBuilder script = new StringBuilder();

        // To allow the script to work properly, ensure an ID has been set.
        String id = attributes.get("id");
        if (id == null) {
            id = WebUtil.nextId();
            attributes.put("id", id);
        }

        // Wrap the script in a function for local variable scoping and a try/catch.
        script.append("(function() { try {");

        // Store the current timestamp and the arguments. The timestamp is used to ensure the div does not contain
        // out-of-date content.
        script.append("var refreshTimestamp = Date.now();");
        script.append("var contentTimestamp = 0;");
        script.append("const refreshInterval = ").append(refreshInterval).append(";");
        script.append("const staleInterval = ").append(staleInterval).append(";");
        script.append("const id = '").append(id).append("';");
        script.append("var endpointIndex = 0;");
        String separator = "";
        script.append("const endpoints = [");
        for (String endpoint : endpoints) {
            script.append(separator).append("'").append(endpoint).append("'");
            separator = ", ";
        }
        script.append("];");

        // This is a simple Ajax update performed at the specified interval (ms). The indentation is for readability in
        // this code, not formatting in the rendered page. The standard HTTP header, Last-Modified, is used for
        // determining whether the new content should replace the previous content. Note that the default value for a
        // missing header is 0, and the replace is done with a greater-than-or-equal-to comparison, so content will
        // refresh normally if this header is missing on all responses.
        script.append("function refresh() {");
        script.append("  var requestTimestamp = Date.now();");
        script.append("  var refreshRequest = new XMLHttpRequest();");
        script.append("  refreshRequest.onreadystatechange = function() {");
        script.append("    if (this.readyState == XMLHttpRequest.DONE && this.status == 200) {");
        script.append("      var newContentTimestamp = getLastModifiedTimestamp(this);");
        script.append("      if (newContentTimestamp >= contentTimestamp) {");
        script.append("        contentTimestamp = newContentTimestamp;");
        script.append("        document.getElementById(id).innerHTML = this.responseText;");
        script.append("        refreshTimestamp = requestTimestamp;");
        script.append("        if (refreshTimestamp >= Date.now() - staleInterval) {");
        script.append("          document.getElementById(id).style.opacity = 1.0;");
        script.append("        }");
        script.append("      }");
        script.append("    }");
        script.append("  };");
        script.append("  refreshRequest.open('GET', endpoints[endpointIndex], true);");
        script.append("  endpointIndex = (endpointIndex + 1) % endpoints.length;");
        script.append("  refreshRequest.send();");
        script.append("  setTimeout(refresh, refreshInterval);");
        script.append("}");

        // This is a helper function to get the last-modified timestamp.
        script.append("function getLastModifiedTimestamp(request) {");
        script.append("  var headers = request.getAllResponseHeaders().toLowerCase();");
        script.append("  var timestamp = 0;");
        script.append("  if (typeof headers === 'string') {");
        script.append("    var split = headers.split(/[\\r\\n]+/g);");
        script.append("    split.forEach(function(header) {");
        script.append("      if (header.startsWith('last-modified: ')) {");
        script.append("        var date = new Date(header.replace('last-modified: ', ''));");
        script.append("        timestamp = date.getTime();");
        script.append("      }");
        script.append("    })");
        script.append("  }");
        script.append("  return timestamp;");
        script.append("}");

        // Trigger the first refresh.
        if (immediateRefresh) {
            script.append("refresh();");
        } else {
            script.append("setTimeout(refresh, refreshInterval);");
        }

        // Set a second periodic function to indicate that the content is stale if its age is more than the stale
        // interval.
        script.append("setInterval(function() {");
        script.append("  if (refreshTimestamp < Date.now() - staleInterval) {");
        script.append("    document.getElementById(id).style.opacity = 0.5;");
        script.append("  }");
        script.append("}, staleInterval / 4);");

        // Close the function that wraps the script.
        script.append("} catch (error) { console.log(error); } })();");

        return new Script(script.toString());
    }
}
