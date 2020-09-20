package co.nyzo.verifier.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class JsonArray {

    private List<Object> objects;

    public JsonArray(List<Object> objects) {
        this.objects = new ArrayList<>(objects);
    }

    public int length() {
        return objects.size();
    }

    public Object get(int index) {
        return objects.get(index);
    }

    public String getString(int index, String defaultValue) {
        Object object = index >= 0 && index < objects.size() ? objects.get(index) : null;
        return object == null ? defaultValue : object + "";
    }

    public double getDouble(int index, double defaultValue) {
        double result = defaultValue;
        try {
            result = Double.parseDouble(getString(index, ""));
        } catch(Exception ignored){ }

        return result;
    }
}
