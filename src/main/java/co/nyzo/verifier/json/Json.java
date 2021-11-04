package co.nyzo.verifier.json;

import co.nyzo.verifier.util.PreferencesUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Json {

    // Like everything Nyzo, this is class is purpose-built to do the job it is supposed to do, minimally and
    // efficiently. We have no reason, at this time, to parse long JSON strings. If you wish to use a higher value,
    // though, it can be set in your preferences file.
    private static final String maximumJsonStringLengthKey = "json_maximum_string_length";
    public static int maximumJsonStringLength = PreferencesUtil.getInt(maximumJsonStringLengthKey, 10000);

    // Instances of this class are used to store state for parsing.
    private String jsonString;
    private int braceLevel;
    private int bracketLevel;
    private boolean inQuotes;
    private int keyStart;
    private int valueStart;
    private int index;
    private String key;

    private Json(String jsonString) {
        this.jsonString = jsonString;
        this.braceLevel = 0;
        this.bracketLevel = 0;
        this.inQuotes = false;
        this.keyStart = -1;
        this.valueStart = -1;
        this.index = 0;
        this.key = null;
    }


    private boolean atObjectRoot() {
        return braceLevel == 1 && bracketLevel == 0;
    }

    private boolean atArrayRoot() {
        return braceLevel == 0 && bracketLevel == 1;
    }

    private void markKeyStart() {
        keyStart = index + 1;
    }

    private void pushKey() {
        key = jsonString.substring(keyStart, index);
    }

    private void markValueStart() {
        valueStart = index;
    }

    private void processValue(Map<String, Object> map) {
        String value = jsonString.substring(valueStart, index + 1);
        map.put(key, parse(value));

        keyStart = -1;
        key = null;
    }

    private void processValue(List<Object> list) {
        String value = jsonString.substring(valueStart, index + 1);
        list.add(parse(value));

        valueStart = -1;
    }

    public static Object parse(String jsonString) {

        // Replace escaped quotes with the null character.
        jsonString = jsonString.trim().replace("\\\"", "\0");

        Object result = null;
        if (jsonString.length() <= maximumJsonStringLength) {
            jsonString = jsonString.trim();
            if (jsonString.startsWith("{")) {
                result = parseJsonObject(jsonString);
            } else if (jsonString.startsWith("[")) {
                result = parseJsonArray(jsonString);
            } else {
                // Remove JSON markers from the string.
                if (jsonString.startsWith(":")) {
                    jsonString = jsonString.substring(1);
                }
                if (jsonString.endsWith("}") || jsonString.endsWith("]") || jsonString.endsWith(",")) {
                    jsonString = jsonString.substring(0, jsonString.length() - 1);
                }
                if (jsonString.startsWith("\"") && jsonString.endsWith("\"")) {
                    jsonString = jsonString.substring(1, jsonString.length() - 1);
                }

                // Replace the null character with quotes.
                result = jsonString.replace('\0', '"');
            }
        }

        return result;
    }

    private static JsonObject parseJsonObject(String jsonString) {

        Map<String, Object> map = new HashMap<>();
        char[] characters = jsonString.toCharArray();
        Json state = new Json(jsonString);
        for (char character : characters) {
            if (character == '"') {
                state.inQuotes = !state.inQuotes;

                // Extract strings at the root level.
                if (state.atObjectRoot()) {
                    if (state.key == null) {
                        if (state.inQuotes) {
                            state.markKeyStart();
                        } else {
                            state.pushKey();
                        }
                    } else {
                        if (state.inQuotes) {
                            state.markValueStart();
                        } else {
                            state.processValue(map);
                        }
                    }
                }
            } else if (!state.inQuotes) {
                // Ascend and descend braces and brackets.
                if (character == '{') {
                    if (state.atObjectRoot() && state.key != null) {
                        state.markValueStart();
                    }
                    state.braceLevel++;
                } else if (character == '[') {
                    if (state.atObjectRoot() && state.key != null) {
                        state.markValueStart();
                    }
                    state.bracketLevel++;
                } else if (character == '}') {
                    if (state.atObjectRoot() && state.key != null && state.valueStart > 0) {
                        state.processValue(map);
                    }
                    state.braceLevel--;
                } else if (character == ']') {
                    state.bracketLevel--;
                    if (state.atObjectRoot() && state.key != null && state.valueStart > 0) {
                        state.processValue(map);
                    }
                } else if (character == ':' && state.atObjectRoot() && state.key != null) {
                    state.markValueStart();
                } else if (character == ',' && state.atObjectRoot() && state.key != null && state.valueStart > 0) {
                    state.processValue(map);
                }
            }

            // Increment the index.
            state.index++;
        }

        return new JsonObject(map);
    }

    private static JsonArray parseJsonArray(String jsonString) {

        List<Object> list = new ArrayList<>();
        char[] characters = jsonString.toCharArray();
        Json state = new Json(jsonString);
        for (char character : characters) {
            if (character == '"') {
                state.inQuotes = !state.inQuotes;

                // Extract strings at the bottom level.
                if (state.atArrayRoot()) {
                    if (state.inQuotes) {
                        state.markValueStart();
                    } else {
                        state.processValue(list);
                    }
                }
            } else if (!state.inQuotes) {
                // Ascend and descend braces and brackets.
                if (character == '{') {
                    if (state.atArrayRoot()) {
                        state.markValueStart();
                    }
                    state.braceLevel++;
                } else if (character == '[') {
                    if (state.atArrayRoot()) {
                        state.markValueStart();
                    }
                    state.bracketLevel++;
                } else if (character == '}') {
                    state.braceLevel--;
                    if (state.atArrayRoot() && state.valueStart > 0) {
                        state.processValue(list);
                    }
                } else if (character == ']') {
                    state.bracketLevel--;
                    if (state.atArrayRoot() && state.valueStart > 0) {
                        state.processValue(list);
                    }
                }
            }

            // Increment the index.
            state.index++;
        }

        return new JsonArray(list);
    }

    public static Object traverse(Object json, Object... keys) {
        // This is a convenience method to quickly dig into a nested JSON structure.
        Object current = json;
        boolean valid = true;
        for (Object key : keys) {
            if (key instanceof String && current instanceof JsonObject) {
                current = ((JsonObject) current).get((String) key);
            } else if (key instanceof Integer && current instanceof JsonArray) {
                current = ((JsonArray) current).get((Integer) key);
            } else {
                valid = false;
            }
        }

        return valid ? current : null;
    }

    public static JsonArray traverseGetArray(Object json, Object... keys) {
        Object result = traverse(json, keys);
        return result instanceof JsonArray ? (JsonArray) result : null;
    }

    public static JsonObject traverseGetObject(Object json, Object... keys) {
        Object result = traverse(json, keys);
        return result instanceof JsonObject ? (JsonObject) result : null;
    }
}
