package co.nyzo.verifier.web.elements;

public class Script extends HtmlTag {

    public Script(String content) {
        addRaw(content);
    }

    @Override
    public String getName() {
        return "script";
    }
}

