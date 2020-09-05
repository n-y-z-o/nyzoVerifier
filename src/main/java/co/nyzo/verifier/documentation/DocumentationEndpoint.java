package co.nyzo.verifier.documentation;

import co.nyzo.verifier.Version;
import co.nyzo.verifier.web.EndpointRequest;
import co.nyzo.verifier.web.EndpointResponseProvider;
import co.nyzo.verifier.web.EndpointResponse;
import co.nyzo.verifier.web.WebUtil;
import co.nyzo.verifier.web.elements.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DocumentationEndpoint implements EndpointResponseProvider {

    private String path;
    private File file;
    private String title;
    private DocumentationEndpointType type;
    private DocumentationEndpoint parent;
    private List<DocumentationEndpoint> children;

    public DocumentationEndpoint(String path, File file) {
        this.path = processPath(path);
        this.file = file.isDirectory() ? new File(file, "index.html") : file;
        this.type = determineType(this.file);
        this.title = findTitle(this.file, this.type);
        this.children = new ArrayList<>();
    }

    public String getPath() {
        return path;
    }

    public File getFile() {
        return file;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public DocumentationEndpointType getType() {
        return type;
    }

    public DocumentationEndpoint getParent() {
        return parent;
    }

    public void setParent(DocumentationEndpoint parent) {
        this.parent = parent;
    }

    public void addChild(DocumentationEndpoint child) {
        children.add(child);
        child.setParent(this);
    }

    public int getNumberOfChildren() {
        return children.size();
    }

    public DocumentationEndpoint getChild(int index) {
        return index >= 0 && index < children.size() ? children.get(index) : null;
    }

    private static String processPath(String path) {

        // Split the path into components.
        String[] components = path.split("/");

        // Process and reassemble the components.
        StringBuilder reassembled = new StringBuilder();
        for (String component : components) {
            // Remove the ordering index, if present.
            int underscoreIndex = -2;
            for (int i = 0; i < component.length() - 1 && underscoreIndex == -2; i++) {
                if (component.charAt(i) == '_') {
                    underscoreIndex = i;
                } else if (component.charAt(i) < '0' || component.charAt(i) > '9') {
                    underscoreIndex = -1;
                }
            }
            if (underscoreIndex >= 0) {
                component = component.substring(underscoreIndex + 1);
            }

            // Add the component to the reassembled path.
            if (!component.isEmpty()) {
                reassembled.append("/").append(component);
            }
        }

        if (reassembled.length() == 0) {
            reassembled.append("/");
        }

        return reassembled.toString();
    }

    private static String findTitle(File file, DocumentationEndpointType type) {
        String title = null;
        if (!file.isDirectory() && type == DocumentationEndpointType.Html) {
            try {
                String fileContents = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())),
                        StandardCharsets.UTF_8);
                for (int i = 0; i < 4 && title == null; i++) {
                    String openTag = "<h" + (i + 1) + ">";
                    String closeTag = ("</h" + (i + 1) + ">");
                    int startIndex = fileContents.indexOf(openTag);
                    if (startIndex >= 0) {
                        int endIndex = fileContents.indexOf(closeTag, startIndex);
                        if (endIndex >= 0) {
                            title = fileContents.substring(startIndex + openTag.length(), endIndex);
                        }
                    }
                }
            } catch (Exception ignored) { }
        }

        // If the title is null, use the path to determine the title.
        if (title == null) {
            title = file.getName().toLowerCase().equals("index.html") ? file.getParentFile().getName() : file.getName();
            if (title.toLowerCase().endsWith(".html")) {
                title = title.substring(0, title.length() - 5);
            }
        }

        return title;
    }

    private static DocumentationEndpointType determineType(File file) {

        DocumentationEndpointType type;
        String filename = file.getName().toLowerCase();
        if (filename.endsWith(".css")) {
            type = DocumentationEndpointType.Css;
        } else if (filename.endsWith(".ico")) {
            type = DocumentationEndpointType.Ico;
        } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            type = DocumentationEndpointType.Jpeg;
        } else if (filename.endsWith(".png")) {
            type = DocumentationEndpointType.Png;
        } else if (file.exists()) {
            type = DocumentationEndpointType.Html;
        } else {
            type = DocumentationEndpointType.Empty;
        }

        return type;
    }

    @Override
    public EndpointResponse getResponse(EndpointRequest request) {

        EndpointResponse result;
        if (type == DocumentationEndpointType.Html) {
            result = getResponseForHtml();
        } else {
            result = getResponseForRaw();
        }

        return result;
    }

    public EndpointResponse getResponseForHtml() {

        // Make the HTML page.
        Html html = (Html) new Html().attr("lang", "en");

        // Add the head and body to the page.
        Head head = (Head) html.add(new Head().addStandardMetadata(title));
        Body body = (Body) html.add(new Body().attr("style", "font-family: sans-serif;"));

        // Get the contents of the file for this page.
        List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(file.getAbsolutePath()));
        } catch (Exception ignored) {
            lines = new ArrayList<>();
        }

        // Add the hover button styles to the head.
        head.add(WebUtil.hoverButtonStyles);

        // Add the styles from the page.
        for (String line : lines) {
            line = getLink(line);
            if (!line.isEmpty()) {
                head.add(new RawHtml(line));
            }
        }

        // Add the breadcrumbs to the top of the page.
        if (!path.equals("/")) {
            List<DocumentationEndpoint> endpointPath = new ArrayList<>();
            DocumentationEndpoint endpoint = this;
            while (endpoint != null) {
                endpointPath.add(0, endpoint);
                endpoint = endpoint.getParent();
            }
            for (int i = 0; i < endpointPath.size(); i++) {
                if (i > 0) {
                    body.add(new RawHtml("&rarr;"));
                }
                body.add(new A().attr("class", "hover-button").attr("href", endpointPath.get(i).getPath())
                        .addRaw(endpointPath.get(i).getTitle()));
            }
            body.add(new Hr());
        }

        // Add the contents for the page.
        if (!lines.isEmpty()) {
            StringBuilder contents = new StringBuilder();
            String separator = "";
            for (String line : lines) {
                // Filter CSS links and add all non-empty lines.
                line = removeLink(line);
                if (!line.isEmpty()) {
                    contents.append(separator).append(line);
                    separator = "\n";
                }
            }
            body.add(new RawHtml(contents.toString()));
        }

        // Add buttons for all HTML children.
        for (DocumentationEndpoint child : children) {
            if (child.getType() == DocumentationEndpointType.Html) {
                body.add(new A().attr("class", "hover-button").attr("href", child.getPath()).addRaw(child.getTitle()));
            }
        }

        // If this it the root, add the version number.
        if (path.equals("/")) {
            body.add(new Hr().attr("style", "margin-top: 2rem;"));
            body.add(new P("Nyzo documentation server, version " + Version.getVersion()).attr("style",
                    "font-style: italic;"));
        }

        return new EndpointResponse(html.renderByteArray(), type.getContentType());
    }

    public EndpointResponse getResponseForRaw() {

        byte[] result = new byte[0];
        if (file.exists()) {
            try {
                result = Files.readAllBytes(Paths.get(file.getAbsolutePath()));
            } catch (Exception ignored) { }
        }

        return new EndpointResponse(result, type.getContentType());
    }

    private static String getLink(String htmlLine) {
        int startIndex = htmlLine.indexOf("<link ");
        int endIndex = startIndex < 0 ? -1 : htmlLine.indexOf(">", startIndex + 1);
        return startIndex >= 0 && endIndex > 0 ? htmlLine.substring(startIndex, endIndex + 1) : "";
    }

    private static String removeLink(String htmlLine) {

        int startIndex = htmlLine.indexOf("<link ");
        int endIndex = startIndex < 0 ? -1 : htmlLine.indexOf(">", startIndex + 1);

        String result;
        if (startIndex < 0 || endIndex < 0) {
            result = htmlLine;
        } else {
            result = "";
            if (startIndex > 0) {
                result += htmlLine.substring(0, startIndex);
            }
            if (endIndex < htmlLine.length() - 1) {
                result += htmlLine.substring(endIndex + 1);
            }
        }

        return result;
    }

    @Override
    public String toString() {
        return "[DocumentationEndpoint:" + path + "]";
    }
}
