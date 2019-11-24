package co.nyzo.verifier.documentation;

import co.nyzo.verifier.web.EndpointResponse;

public enum DocumentationEndpointType {

    Css(EndpointResponse.contentTypeCss),
    Empty(EndpointResponse.contentTypeHtml),
    Html(EndpointResponse.contentTypeHtml),
    Jpeg(EndpointResponse.contentTypeJpeg),
    Png(EndpointResponse.contentTypePng);

    private String contentType;

    DocumentationEndpointType(String contentType) {
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }
}
