package co.nyzo.verifier.web.elements;

public class H3 extends HtmlTag {

    public H3(String content) {
        addRaw(content);
    }

    @Override
    public String getName() {
        return "h3";
    }
}

