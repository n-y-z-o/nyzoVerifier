package co.nyzo.verifier.relay;

import co.nyzo.verifier.util.LogUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.web.*;

import java.io.File;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class RelayEndpoint implements EndpointResponseProvider {

    private static final long maximumSize = 1024L * 1024L * 10L;

    private boolean isFileEndpoint;
    private String sourceEndpoint;
    private long interval;
    private EndpointResponse cachedWebResponse;
    private long lastRefreshTimestamp;
    private long lastResponseTimestamp;

    public RelayEndpoint(String sourceEndpoint) {
        this.isFileEndpoint = sourceEndpoint.startsWith("file:/");
        this.sourceEndpoint = sourceEndpoint;
        this.interval = Long.MAX_VALUE;
    }

    public RelayEndpoint(String sourceEndpoint, long interval) {
        this.isFileEndpoint = sourceEndpoint.startsWith("file:/");
        this.sourceEndpoint = sourceEndpoint;
        this.interval = interval;
    }

    @Override
    public EndpointResponse getResponse(EndpointRequest request) {
        EndpointResponse response;
        if (isFileEndpoint) {
            // Get the path. If the path is a directory, get the filename from the request endpoint.
            Path filePath = Paths.get(sourceEndpoint.replace("file:", "").replaceAll("/+", "/"));
            if (filePath.toFile().isDirectory()) {
                String requestPath = request.getEndpoint().getPath();
                int lastPathSeparatorIndex = requestPath.lastIndexOf("/");
                if (lastPathSeparatorIndex > 0) {
                    String filename = requestPath.substring(lastPathSeparatorIndex + 1);
                    filePath = Paths.get(filePath.toString(), filename);
                }
            }

            byte[] result;
            String contentType;
            try {
                result = Files.readAllBytes(filePath);
                contentType = EndpointResponse.contentTypeForFile(filePath.toString());
            } catch (Exception e) {
                result = ("error reading file: " + PrintUtil.printException(e)).getBytes(StandardCharsets.UTF_8);
                contentType = EndpointResponse.contentTypeText;
            }
            response = new EndpointResponse(result, contentType);
            response.setHeader("Last-Modified", WebUtil.imfFixdateString(System.currentTimeMillis()));
            response.setHeader("Access-Control-Allow-Origin", "*");
        } else {
            response = cachedWebResponse;
        }

        return response;
    }

    public void refresh() {
        // Only refresh web endpoints that are out of date.
        if (!isFileEndpoint && lastRefreshTimestamp < System.currentTimeMillis() - interval) {
            LogUtil.println("refreshing endpoint: " + sourceEndpoint);
            try {
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
                    long refreshTimestamp = System.currentTimeMillis();
                    cachedWebResponse = new EndpointResponse(result, connection.getContentType());
                    cachedWebResponse.setHeader("Last-Modified", WebUtil.imfFixdateString(refreshTimestamp));
                    cachedWebResponse.setHeader("Access-Control-Allow-Origin", "*");

                    // Set the refresh timestamp.
                    lastRefreshTimestamp = refreshTimestamp;
                }
            } catch (Exception ignored) { }
        }
    }
}
