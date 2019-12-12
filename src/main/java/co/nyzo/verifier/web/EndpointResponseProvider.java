package co.nyzo.verifier.web;

public interface EndpointResponseProvider {

    EndpointResponse getResponse(EndpointRequest request);
}
