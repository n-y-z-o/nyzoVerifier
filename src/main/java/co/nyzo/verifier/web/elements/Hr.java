package co.nyzo.verifier.web.elements;

public class Hr extends HtmlTag {

    public Hr() {
        setIncludeClosingTag(false);
    }

    @Override
    public String getName() {
        return "hr";
    }
}
