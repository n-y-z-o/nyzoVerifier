package co.nyzo.verifier.web;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class EndpointResponse {

    public static final String contentTypeDefault = "text/html;charset=UTF-8";
    public static final String contentTypeCss = "text/css";

    private byte[] content;
    private Map<String, String> headers = new HashMap<>();

    public EndpointResponse(byte[] content) {
        this.content = content;
        headers.put("Content-type", contentTypeDefault);
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
