package co.nyzo.verifier.web.elements;

public class Meta extends HtmlTag {

    public Meta() {
        setIncludeClosingTag(false);
    }

    @Override
    public String getName() {
        return "meta";
    }
}
