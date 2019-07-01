package co.nyzo.verifier.web.elements;

public class Title extends HtmlTag {

    public Title(String content) {
        addRaw(content);
    }

    @Override
    public String getName() {
        return "title";
    }
}

