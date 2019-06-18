package co.nyzo.verifier.web.elements;

public class Head extends HtmlTag {

    private static final String standardMetadata = "<meta name=\"format-detection\" content=\"telephone=no\" />" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, maximum-scale=1\" />";

    @Override
    public String getName() {
        return "head";
    }

    public void addStandardMetadata() {
        addRaw(standardMetadata);
    }
}
