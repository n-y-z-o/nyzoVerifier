package co.nyzo.verifier.web.elements;

public class Head extends HtmlTag {

    @Override
    public String getName() {
        return "head";
    }

    public Head addStandardMetadata() {
        add(new Meta().attr("name", "format-detection").attr("content", "telephone=no"));
        add(new Meta().attr("name", "viewport")
                .attr("content", "width=device-width, initial-scale=1, maximum-scale=1"));
        return this;
    }
}
