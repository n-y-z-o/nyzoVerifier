package co.nyzo.verifier.web.elements;

public class H1 extends HtmlTag {

    public H1(String content) {
        addRaw(content);
    }

    @Override
    public String getName() {
        return "h1";
    }
}
