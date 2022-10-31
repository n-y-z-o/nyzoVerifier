package co.nyzo.verifier.json;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Collection;

public class JsonRenderer {

    public static String toJson(Object object) {

        String result;
        if (object == null) {
            result = "null";
        } else if (object instanceof String) {
            if (isNumeric(object)) {
                result = object + "";
            } else {
                result = "\"" + escapeStringForJson((String) object) + "\"";
            }
        } else if (object instanceof Number || object instanceof Boolean) {
            result = object.toString();
        } else if (object instanceof Collection) {
            result = jsonForCollection((Collection) object);
        } else if (object instanceof Array || object instanceof int[] || object instanceof long[]) {
            result = jsonForArray(object);
        } else if (object instanceof JsonRenderable) {
            result = ((JsonRenderable) object).renderJson();
        } else if (object instanceof JsonRenderable[]) {
            JsonRenderable[] array = (JsonRenderable[]) object;
            StringBuilder arrayResult = new StringBuilder("");
            String separator = "";
            if (array.length > 1) {
                arrayResult.append("[");
            }
            for (JsonRenderable renderable : array) {
                arrayResult.append(separator).append(renderable.renderJson());
                separator = ",";
            }
            if (array.length > 1) {
                arrayResult.append("]");
            }
            result = arrayResult.toString();
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

    private static String jsonForArray(Object array) {

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

    private static boolean isNumeric(Object value) {
        boolean isNumeric = false;
        if (value instanceof Number) {
            isNumeric = true;
        } else if (value instanceof String) {
            try {
                Double.parseDouble((String) value);
                isNumeric = true;
            } catch (Exception ignored) { }
        }

        return isNumeric;
    }
}
