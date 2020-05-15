package co.nyzo.verifier.relay;

import co.nyzo.verifier.util.LogUtil;
import co.nyzo.verifier.web.EndpointRequest;
import co.nyzo.verifier.web.EndpointResponse;
import co.nyzo.verifier.web.EndpointResponseProvider;
import co.nyzo.verifier.web.WebUtil;

import java.io.File;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;

public class RelayEndpoint implements EndpointResponseProvider {

    private static final long maximumSize = 1024L * 1024L * 10L;

    private String sourceEndpoint;
    private long interval;
    private EndpointResponse response;
    private long lastRequestTimestamp;
    private long lastResponseTimestamp;

    public RelayEndpoint(String sourceEndpoint, long interval) {
        this.sourceEndpoint = sourceEndpoint;
        this.interval = interval;
    }

    @Override
    public EndpointResponse getResponse(EndpointRequest request) {
        return response;
    }

    public void refresh() {
        if (lastRequestTimestamp < System.currentTimeMillis() - interval) {
            LogUtil.println("refreshing endpoint: " + sourceEndpoint);
            lastRequestTimestamp = System.currentTimeMillis();
            try {
                if (sourceEndpoint.startsWith("file://")) {
                    String path = sourceEndpoint.substring("file:/".length());
                    byte[] result = Files.readAllBytes(Paths.get(path));
                    String contentType = EndpointResponse.contentTypeForFile(path);
                    response = new EndpointResponse(result, contentType);
                    response.setHeader("Last-Modified", WebUtil.imfFixdateString(System.currentTimeMillis()));
                } else {
                    URLConnection connection = new URL(sourceEndpoint).openConnection();
                    connection.setConnectTimeout(2000);  // 2 seconds
                    connection.setReadTimeout(10000);    // 10 seconds
                    if (connection.getContentLength() <= maximumSize) {
                        byte[] result = new byte[connection.getContentLength()];
                        ByteBuffer buffer = ByteBuffer.wrap(result);
                        ReadableByteChannel channel = Channels.newChannel(connection.getInputStream());
                        int bytesRead;
                        int totalBytesRead = 0;
                        while ((bytesRead = channel.read(buffer)) > 0) {
                            totalBytesRead += bytesRead;
                        }
                        channel.close();

                        // Build the response and set the last-modified header.
                        response = new EndpointResponse(result, connection.getContentType());
                        response.setHeader("Last-Modified", WebUtil.imfFixdateString(System.currentTimeMillis()));
                        response.setHeader("Access-Control-Allow-Origin", "*");
                    }
                }
            } catch (Exception ignored) { }
        }
    }
}
