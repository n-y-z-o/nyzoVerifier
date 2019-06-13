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

    public HtmlElement attr(String name, String value) {
        attributes.put(name, value);
        return this;
    }

    public HtmlElement addRaw(String rawHtml) {
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

        // This is a simple AJAX update performed at the specified interval (ms). The indentation is for readability in
        // this code, not formatting in the rendered page.
        script.append("setInterval(function() {");
        script.append("  var refreshRequest = new XMLHttpRequest();");
        script.append("  refreshRequest.onreadystatechange = function() {");
        script.append("    if (this.readyState == 4 && this.status == 200) {");
        script.append("      document.getElementById('").append(id).append("').innerHTML = this.responseText;");
        script.append("    }");
        script.append("  };");
        script.append("  refreshRequest.open('GET', '").append(endpoint).append("', true);");
        script.append("  refreshRequest.send();");
        script.append("}, ").append(interval).append(");");

        return new Script(script.toString());
    }
}
