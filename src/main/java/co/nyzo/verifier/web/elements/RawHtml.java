package co.nyzo.verifier.web.elements;

public class RawHtml implements HtmlElement {

    private String content;

    public RawHtml(String content) {
        this.content = content;
    }

    @Override
    public String render() {
        return content;
    }
}
