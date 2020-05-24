package co.nyzo.verifier.web;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class EndpointResponse {

    public static final String contentTypeCss = "text/css";
    public static final String contentTypeHtml = "text/html;charset=UTF-8";
    public static final String contentTypeIco = "image/x-icon";
    public static final String contentTypeJpeg = "image/jpeg";
    public static final String contentTypeJson = "application/json";
    public static final String contentTypeOctetStream = "application/octet-stream";
    public static final String contentTypePdf = "application/pdf";
    public static final String contentTypePng = "image/png";
    public static final String contentTypeText = "text/plain;charset=UTF-8";

    public static final String contentTypeDefault = contentTypeHtml;

    private byte[] content;
    private Map<String, String> headers = new HashMap<>();
    private HttpStatusCode statusCode;

    public EndpointResponse(byte[] content) {
        this(content, contentTypeDefault);
    }

    public EndpointResponse(byte[] content, String contentType) {
        this(content, contentType, HttpStatusCode.Ok200);
    }

    public EndpointResponse(byte[] content, String contentType, HttpStatusCode statusCode) {
        this.content = content;
        headers.put("Content-type", contentType);
        this.statusCode = statusCode;
    }

    public byte[] getContent() {
        return content;
    }

    public Set<String> getHeaderNames() {
        return headers.keySet();
    }

    public String getHeader(String name) {
        return headers.getOrDefault(name, "");
    }

    public void setHeader(String name, String value) {
        headers.put(name, value);
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    public static String contentTypeForFile(String path) {
        path = path == null ? "" : path.toLowerCase();
        String contentType;
        if (path.endsWith(".css")) {
            contentType = contentTypeCss;
        } else if (path.endsWith(".html") || path.endsWith(".htm")) {
            contentType = contentTypeHtml;
        } else if (path.endsWith(".ico")) {
            contentType = contentTypeIco;
        } else if (path.endsWith(".jpeg") || path.endsWith(".jpg") || path.endsWith(".jfif")) {
            contentType = contentTypeJpeg;
        } else if (path.endsWith(".json")) {
            contentType = contentTypeJson;
        } else if (path.endsWith(".pdf")) {
            contentType = contentTypePdf;
        } else if (path.endsWith(".png")) {
            contentType = contentTypePng;
        } else if (path.endsWith(".txt")) {
            contentType = contentTypeText;
        } else {
            contentType = contentTypeOctetStream;
        }

        return contentType;
    }
}
