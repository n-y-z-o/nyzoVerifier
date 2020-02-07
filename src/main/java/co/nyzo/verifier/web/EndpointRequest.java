package co.nyzo.verifier.web;

import java.util.Map;

public class EndpointRequest {

    private Endpoint endpoint;
    private Map<String, String> queryParameters;
    private Map<String, String> postParameters;
    private byte[] sourceIpAddress;

    public EndpointRequest(Endpoint endpoint, Map<String, String> queryParameters, Map<String, String> postParameters,
                           byte[] sourceIpAddress) {
        this.endpoint = endpoint;
        this.queryParameters = queryParameters;
        this.postParameters = postParameters;
        this.sourceIpAddress = sourceIpAddress;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public Map<String, String> getQueryParameters() {
        return queryParameters;
    }

    public Map<String, String> getPostParameters() {
        return postParameters;
    }

    public byte[] getSourceIpAddress() {
        return sourceIpAddress;
    }
}
