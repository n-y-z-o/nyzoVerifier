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
    public static final String contentTypePng = "image/png";
    public static final String contentTypeText = "text/plain;charset=UTF-8";

    public static final String contentTypeDefault = contentTypeHtml;


    private byte[] content;
    private Map<String, String> headers = new HashMap<>();

    public EndpointResponse(byte[] content) {
        this(content, contentTypeDefault);
    }

    public EndpointResponse(byte[] content, String contentType) {
        this.content = content;
        headers.put("Content-type", contentType);
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
}
