package co.nyzo.verifier.documentation;

import co.nyzo.verifier.web.EndpointResponse;

public enum DocumentationEndpointType {

    Css(EndpointResponse.contentTypeCss),
    Empty(EndpointResponse.contentTypeHtml),
    Html(EndpointResponse.contentTypeHtml),
    HtmlFragment(EndpointResponse.contentTypeHtml),
    Ico(EndpointResponse.contentTypeIco),
    Jpeg(EndpointResponse.contentTypeJpeg),
    Png(EndpointResponse.contentTypePng),
    Text(EndpointResponse.contentTypeText);

    private String contentType;

    DocumentationEndpointType(String contentType) {
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }
}
