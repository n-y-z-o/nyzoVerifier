package co.nyzo.verifier.web.elements;

public class Html extends HtmlTag {

    private static final String docType = ("<!doctype html>");

    @Override
    public String render() {
        return docType + super.render();
    }

    @Override
    public String getName() {
        return "html";
    }
}
