package co.nyzo.verifier.web.elements;

public class H2 extends HtmlTag {

    public H2(String content) {
        addRaw(content);
    }

    @Override
    public String getName() {
        return "h2";
    }
}
