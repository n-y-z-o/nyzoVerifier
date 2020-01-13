package co.nyzo.verifier.client;

import co.nyzo.verifier.client.commands.PublicNyzoStringCommand;
import co.nyzo.verifier.util.UpdateUtil;
import co.nyzo.verifier.web.WebUtil;
import co.nyzo.verifier.web.elements.*;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class SimpleExecutionResult implements ExecutionResult {

    private CommandTable result;
    private List<String> notices;
    private List<String> errors;

    public SimpleExecutionResult(CommandTable result, List<String> notices, List<String> errors) {
        // For consistency in the JSON response, ensure that none of the fields are null.
        this.result = result == null ? new CommandTable() : result;
        this.notices = notices == null ? new ArrayList<>() : notices;
        this.errors = errors == null ? new ArrayList<>() : errors;
    }

    public CommandTable getResult() {
        return result;
    }

    public List<String> getNotices() {
        return notices;
    }

    public List<String> getErrors() {
        return errors;
    }

    @Override
    public String toJson() {
        return toJson(this);
    }

    @Override
    public HtmlElement toHtml() {

        // Create the div and add the styles that will be used.
        HtmlElementList resultList = new HtmlElementList();
        resultList.add(new Style(".error { background-color: #f88; padding: 0.3rem; border: 1px solid #c00; " +
                "border-radius: 0.5rem; max-inline-size: fit-content; margin: 0.5rem 0 0.5rem 0; }" +
                ".notice { background-color: #ff8; padding: 0.3rem; border: 1px solid #cc0; border-radius: 0.5rem; " +
                "max-inline-size: fit-content; margin: 0.5rem 0 0.5rem 0; }" +
                ".table { display: table; border: 1px solid gray; border-radius: 0.5rem; margin: 0.5rem 0 0.5rem 0; " +
                "font-size: 0.8rem; }" +
                ".table div { padding: 0.1rem; }" +
                ".header-row > div { background-color: #ddd; }" +
                ".header-row > div:first-child { border-top-left-radius: 0.5rem; }" +
                ".header-row > div:last-child { border-top-right-radius: 0.5rem; }" +
                ".header-row { display: table-row; }" +
                ".header-row > div { display: table-cell; }" +
                ".header-row > div:not(:first-child) { border-left: 1px solid gray; }" +
                ".data-row { display: table-row; }" +
                ".data-row > div { display: table-cell; border-top: 1px solid gray; }" +
                ".data-row > div:not(:first-child) { border-left: 1px solid gray; }" +
                ".row-inverted { display: table-row; }" +
                ".row-inverted > div { display: table-cell; }" +
                ".row-inverted:not(:first-child) > div { border-top: 1px solid gray; }" +
                ".row-inverted > div:first-child { background-color: #ddd; }" +
                ".row-inverted:first-child > div:first-child { border-top-left-radius: 0.5rem; }" +
                ".row-inverted:last-child > div:first-child { border-bottom-left-radius: 0.5rem; }" +
                ".row-inverted > div:not(:first-child) { border-left: 1px solid gray; }" +
                ".extra-wrap { word-break: break-all; }"));

        // Add the errors.
        if (result != null) {
            for (String error : errors) {
                resultList.add(new P(error).attr("class", "error"));
            }
        }

        // Add the notices.
        if (notices != null) {
            for (String notice : notices) {
                resultList.add(new P(notice).attr("class", "notice"));
            }
        }

        // Add the result.
        if (result != null && result.getRows().size() > 0) {
            // Create the table.
            Div tableDiv = (Div) resultList.add(new Div().attr("class", "table"));

            if (result.isInvertedRowsColumns()) {
                // This is the inverted rows/columns case. Render the header to the left.
                int numberOfColumns = 1 + result.getRows().size();
                for (int i = 0; i < result.getHeaders().length; i++) {
                    Div row = (Div) tableDiv.add(new Div().attr("class", "row-inverted"));
                    row.add(new Div().addRaw(result.getHeaders()[i].getLabel()));
                    for (String[] dataRow : result.getRows()) {
                        Div cell = (Div) row.add(new Div().addRaw(dataRow[i]));
                        if (result.getHeaders()[i].isExtraWrapColumn()) {
                            cell.attr("class", "extra-wrap");
                        }
                    }
                }
            } else {
                // This is the non-inverted case. Add the header row.
                Div headerRowDiv = (Div) tableDiv.add(new Div().attr("class", "header-row"));
                CommandTableHeader[] headers = result.getHeaders();
                for (CommandTableHeader header : result.getHeaders()) {
                    headerRowDiv.add(new Div().addRaw(WebUtil.sanitizeString(header.getLabel())));
                }

                // Add the data rows.
                for (String[] row : result.getRows()) {
                    Div dataRowDiv = (Div) tableDiv.add(new Div().attr("class", "data-row"));
                    int numberOfColumns = Math.min(headers.length, row.length);
                    for (int i = 0; i < numberOfColumns; i++) {
                        String value = WebUtil.sanitizeString(row[i]);
                        Div cell = (Div) dataRowDiv.add(new Div().addRaw(value));
                        if (headers[i].isExtraWrapColumn()) {
                            cell.attr("class", "extra-wrap");
                        }
                    }
                }
            }
        }

        return resultList;
    }

    public void toConsole(CommandOutput output) {
        ConsoleUtil.printMessages(getNotices(), "NOTICE: ", ConsoleColor.Yellow, output);
        ConsoleUtil.printMessages(getErrors(), "ERROR: ", ConsoleColor.Red, output);
        ConsoleUtil.printTable(getResult(), output);
    }

    private static String toJson(Object object) {
        String result;
        if (object == null) {
            result = "null";
        } else if (object instanceof String) {
            result = "\"" + escapeStringForJson((String) object) + "\"";
        } else if (object instanceof Integer || object instanceof Long || object instanceof Float ||
                object instanceof Double) {
            result = object.toString();
        } else if (object instanceof Collection) {
            result = jsonForCollection((Collection) object);
        } else if (object instanceof Array) {
            result = jsonForArray((Array) object);
        } else if (object instanceof CommandTable) {
            result = jsonForCommandTable((CommandTable) object);
        } else {
            StringBuilder objectResult = new StringBuilder("{");
            String separator = "";
            for (Method method : object.getClass().getMethods()) {
                String methodName = method.getName();
                if (method.getParameterCount() == 0 && methodName.startsWith("get") && !methodName.equals("getClass") &&
                        methodName.length() >= 4) {
                    try {
                        Object value = method.invoke(object);
                        if (value != null) {
                            String fieldName = (methodName.charAt(3) + "").toLowerCase() + methodName.substring(4);
                            objectResult.append(separator).append("\"").append(fieldName).append("\":")
                                    .append(toJson(value));
                            separator = ",";
                        }
                    } catch (Exception ignored) { }
                }
            }
            objectResult.append("}");
            result = objectResult.toString();
        }

        return result;
    }

    private static String jsonForCollection(Collection collection) {

        StringBuilder result = new StringBuilder("[");
        String separator = "";
        for (Object item : collection) {
            result.append(separator).append(toJson(item));
            separator = ",";
        }
        result.append("]");

        return result.toString();
    }

    private static String jsonForArray(Array array) {

        StringBuilder result = new StringBuilder("[");
        String separator = "";
        int length = Array.getLength(array);
        for (int i = 0; i < length; i++) {
            result.append(separator).append(toJson(Array.get(array, i)));
            separator = ",";
        }
        result.append("]");

        return result.toString();
    }

    private static String jsonForCommandTable(CommandTable table) {

        StringBuilder result = new StringBuilder("[");
        String rowSeparator = "";
        CommandTableHeader[] headers = table.getHeaders();
        for (String[] row : table.getRows()) {
            result.append(rowSeparator).append("{");
            rowSeparator = ",";
            int length = Math.min(row.length, headers.length);
            for (int i = 0; i < length; i++) {
                result.append(i == 0 ? "" : ",").append("\"").append(headers[i].getIdentifier()).append("\":\"")
                        .append(escapeStringForJson(row[i])).append("\"");
            }
            result.append("}");
        }
        result.append("]");

        return result.toString();
    }

    private static String escapeStringForJson(String value) {

        // According the the JSON spec (https://www.json.org/json-en.html), few characters need to be escaped in JSON
        // strings. They are handled below.
        StringBuilder result = new StringBuilder();
        for (char character : value.toCharArray()) {
            switch (character) {
                case '"':
                case '\\':
                case '/':
                    result.append('\\').append(character);
                    break;
                case '\b':
                    result.append("\\b");
                    break;
                case '\f':
                    result.append("\\f");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                default:
                    result.append(character);
            }
        }

        return result.toString();
    }
}
