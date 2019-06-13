package co.nyzo.verifier.web.elements;

public class Html extends HtmlTag {

    private static final String docType = ("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01//EN\" " +
            "\"http://www.w3.org/TR/html4/strict.dtd\">");

    @Override
    public String render() {
        return docType + super.render();
    }

    @Override
    public String getName() {
        return "html";
    }
}
