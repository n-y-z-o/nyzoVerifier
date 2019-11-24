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

    public Head addStandardMetadata(String title) {
        addStandardMetadata();
        add(new Title(title));
        add(new Meta().attr("property", "og:title").attr("content", title));
        add(new Meta().attr("property", "og:description").attr("content", title));
        add(new Meta().attr("property", "og:type").attr("content", "website"));
        return this;
    }
}
