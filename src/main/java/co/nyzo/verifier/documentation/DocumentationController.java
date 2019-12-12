package co.nyzo.verifier.documentation;

import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.util.LogUtil;
import co.nyzo.verifier.util.PreferencesUtil;
import co.nyzo.verifier.web.Endpoint;
import co.nyzo.verifier.web.EndpointResponseProvider;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DocumentationController {

    private static final String dataRootKey = "documentation_data_root";
    private static final String dataRootDirectory = PreferencesUtil.get(dataRootKey);

    public static Map<Endpoint, EndpointResponseProvider> buildEndpointMap() {

        if (dataRootDirectory.isEmpty()) {
            LogUtil.println(ConsoleColor.Yellow.background() + "Please set a documentation data root in preferences (" +
                    dataRootKey + ")" + ConsoleColor.reset);
        }
        LogUtil.println("data root: " + dataRootDirectory);

        Map<Endpoint, EndpointResponseProvider> map = new ConcurrentHashMap<>();

        File rootFile = new File(dataRootDirectory);
        File[] files = { rootFile };
        process(files, rootFile.getAbsolutePath(), map, null);

        return map;
    }

    private static void process(File[] files, String rootFilePath, Map<Endpoint, EndpointResponseProvider> map,
                                DocumentationEndpoint parentEndpoint) {

        if (files != null) {
            // Sort the files by filename ascending.
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File file1, File file2) {
                    return file1.getName().compareTo(file2.getName());
                }
            });

            for (File file : files) {

                // Do not process files that start with '.' or end with '~'. These are common hidden files. Also, do not
                // process files marked as hidden.
                String filename = file.getName();
                String filenameLowercase = filename.toLowerCase();
                if (!file.isHidden() && !filename.startsWith(".") && !filename.endsWith("~")) {

                    // Process files. Recurse into subdirectories. Only accept .html, .png, .jpg, .css, and .ico
                    // extensions.
                    if (file.isDirectory() || (filenameLowercase.endsWith(".html") ||
                            filenameLowercase.endsWith(".png") || filenameLowercase.endsWith(".jpg") ||
                            filenameLowercase.endsWith(".css") || filenameLowercase.endsWith(".ico")) &&
                            !filenameLowercase.equals("index.html")) {

                        // Remove the root and replace backslashes with forward slashes.
                        String path = file.getAbsolutePath().replace(rootFilePath, "");
                        path = path.replace('\\', '/');

                        // If the path ends in ".html", trim it.
                        if (path.toLowerCase().endsWith(".html")) {
                            path = path.substring(0, path.length() - 5);
                        }

                        // Ensure that the path starts with "/".
                        if (!path.startsWith("/")) {
                            path = "/" + path;
                        }

                        // Create the endpoint.
                        DocumentationEndpoint endpoint = new DocumentationEndpoint(path, file);
                        LogUtil.println("loaded documentation endpoint: " + endpoint);
                        map.put(new Endpoint(endpoint.getPath()), endpoint);

                        // Add this endpoint to the parent, if one is available.
                        if (parentEndpoint != null) {
                            parentEndpoint.addChild(endpoint);
                        }

                        if (file.isDirectory()) {
                            process(file.listFiles(), rootFilePath, map, endpoint);
                        }
                    }
                }
            }
        }
    }
}
