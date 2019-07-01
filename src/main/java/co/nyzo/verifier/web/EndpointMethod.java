package co.nyzo.verifier.web;

import java.util.Map;

public interface EndpointMethod {

    EndpointResponse renderByteArray(Map<String, String> queryParameters, byte[] sourceIpAddress);
}
