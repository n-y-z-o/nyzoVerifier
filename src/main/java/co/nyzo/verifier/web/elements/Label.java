package co.nyzo.verifier.web.elements;

public class Label extends HtmlTag {

    public Label(String content) {
        addRaw(content);
    }

    @Override
    public String getName() {
        return "label";
    }
}
