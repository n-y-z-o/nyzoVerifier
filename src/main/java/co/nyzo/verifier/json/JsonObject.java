package co.nyzo.verifier.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class JsonObject {

    private Map<String, Object> objects;

    public JsonObject(Map<String, Object> objects) {
        this.objects = new ConcurrentHashMap<>(objects);
    }

    public Set<String> getKeys() {
        return objects.keySet();
    }

    public Object get(String key) {
        return objects.get(key);
    }

    public String getString(String key, String defaultValue) {
        Object object = objects.get(key);
        return object == null ? defaultValue : object + "";
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        boolean result = defaultValue;
        String value = getString(key, "").toLowerCase();
        if (value.equals("true") || value.equals("t") || value.equals("yes") || value.equals("y") ||
                value.equals("1")) {
            result = true;
        } else if (value.equals("false") || value.equals("f") || value.equals("no") || value.equals("n") ||
                value.equals("0")) {
            result = false;
        }

        return result;
    }

    public double getDouble(String key, double defaultValue) {
        double result = defaultValue;
        if (objects.containsKey(key)) {
            try {
                result = Double.parseDouble(getString(key, "").replace("∩", ""));
            } catch (Exception ignored) { }
        }

        return result;
    }

    public int getInteger(String key, int defaultValue) {
        int result = defaultValue;
        if (objects.containsKey(key)) {
            try {
                result = Integer.parseInt(getString(key, ""));
            } catch (Exception ignored) { }
        }

        return result;
    }

    public long getLong(String key, long defaultValue) {
        long result = defaultValue;
        if (objects.containsKey(key)) {
            try {
                result = Long.parseLong(getString(key, ""));
            } catch (Exception ignored) { }
        }

        return result;
    }

    public List<String> getStringList(String key) {
        List<String> stringList = new ArrayList<>();
        Object arrayObject = get(key);
        if (arrayObject instanceof JsonArray) {
            JsonArray array = (JsonArray) arrayObject;
            for (int i = 0; i < array.length(); i++) {
                stringList.add(array.get(i) + "");
            }
        }

        return stringList;
    }
}
