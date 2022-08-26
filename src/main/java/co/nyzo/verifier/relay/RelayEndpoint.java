package co.nyzo.verifier.relay;

import co.nyzo.verifier.util.LogUtil;
import co.nyzo.verifier.util.PreferencesUtil;
import co.nyzo.verifier.web.*;

import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RelayEndpoint implements EndpointResponseProvider {

    private static final long maximumCachedWebResponseBytes = 1024L * 1024L * 10L;  // 10 MB

    private static final Map<String, FileContentCache> filePathToFileContentMap = new ConcurrentHashMap<>();
    private static final int maximumFileContentMapItems = PreferencesUtil.getInt("relay_endpoint_maximum_cache_items",
            10);

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
            // Get the path. If the path is a directory, get the filename from the request endpoint and change the path
            // to point to the file.
            Path filePath = Paths.get(sourceEndpoint.replace("file:", "").replaceAll("/+", "/"));
            if (filePath.toFile().isDirectory()) {
                String requestPath = request.getEndpoint().getPath();
                int lastPathSeparatorIndex = requestPath.lastIndexOf("/");
                if (lastPathSeparatorIndex > 0) {
                    String filename = requestPath.substring(lastPathSeparatorIndex + 1);
                    filePath = Paths.get(filePath.toString(), filename);
                }
            }

            // First, try to get the content from the cache.
            byte[] result = null;
            String filePathString = filePath.toAbsolutePath().toString();
            long fileTimestamp = filePath.toFile().lastModified();
            FileContentCache contentCache = filePathToFileContentMap.get(filePathString);
            if (contentCache != null && contentCache.getFileTimestamp() == fileTimestamp) {
                // Get the result from the cache and set the timestamp to indicate that the cache was used.
                result = contentCache.getContents();
                contentCache.setLastUsedTimestamp();
            }

            // If the result is null, try to read it from the file.
            if (result == null) {
                try {
                    result = Files.readAllBytes(filePath);

                    // Store the content in the cache.
                    filePathToFileContentMap.put(filePathString, new FileContentCache(result, fileTimestamp));

                    // If the cache is too large, remove the oldest object. The while loop (vs. if) is to provide clear
                    // assurance that an overlooked race condition would not result in size creep over time.
                    while (filePathToFileContentMap.size() > maximumFileContentMapItems) {
                        // Find the oldest item.
                        long oldestTimestamp = Long.MAX_VALUE;
                        String oldestPath = filePathString;
                        for (String path : filePathToFileContentMap.keySet()) {
                            FileContentCache cacheItem = filePathToFileContentMap.get(path);
                            if (cacheItem.getLastUsedTimestamp() < oldestTimestamp) {
                                oldestPath = path;
                                oldestTimestamp = cacheItem.getLastUsedTimestamp();
                            }
                        }

                        // Remove the oldest item.
                        filePathToFileContentMap.remove(oldestPath);
                    }
                } catch (Exception ignored) { }
            }

            // If the result is still null, return a 404 with a content type of text. Otherwise, set the appropriate
            // content type for the file.
            String contentType;
            HttpStatusCode statusCode;
            if (result == null) {
                result = "not found (404)".getBytes(StandardCharsets.UTF_8);
                contentType = EndpointResponse.contentTypeText;
                statusCode = HttpStatusCode.NotFound404;
            } else {
                contentType = EndpointResponse.contentTypeForFile(filePath.toString());
                statusCode = HttpStatusCode.Ok200;
            }

            // Build the result.
            response = new EndpointResponse(result, contentType, statusCode);
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
                if (connection.getContentLength() <= maximumCachedWebResponseBytes) {
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
