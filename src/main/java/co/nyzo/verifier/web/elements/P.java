package co.nyzo.verifier.web.elements;

public class P extends HtmlTag {

    public P(String content) {
        addRaw(content);
    }

    @Override
    public String getName() {
        return "p";
    }
}

