package co.nyzo.verifier.web.elements;

public class Style extends HtmlTag {

    public Style(String content) {
        addRaw(content);
    }

    @Override
    public String getName() {
        return "style";
    }
}
