package co.nyzo.verifier.client;

import co.nyzo.verifier.client.commands.PublicNyzoStringCommand;
import co.nyzo.verifier.util.UpdateUtil;
import co.nyzo.verifier.web.elements.HtmlElement;
import co.nyzo.verifier.web.elements.HtmlElementList;

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
        return null;
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
            result = "\"" + object + "\"";
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
                        .append(row[i]).append("\"");
            }
            result.append("}");
        }
        result.append("]");

        return result.toString();
    }
}
