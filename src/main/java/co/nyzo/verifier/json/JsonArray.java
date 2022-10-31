package co.nyzo.verifier.json;

import java.util.ArrayList;
import java.util.List;

public class JsonArray implements JsonRenderable {

    private final List<Object> objects;

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

    public int getInteger(int index, int defaultValue) {
        int result = defaultValue;
        try {
            result = Integer.parseInt(getString(index, ""));
        } catch(Exception ignored){ }

        return result;
    }

    public long getLong(int index, long defaultValue) {
        long result = defaultValue;
        try {
            result = Long.parseLong(getString(index, ""));
        } catch(Exception ignored){ }

        return result;
    }

    public int[] toIntegerArray() {
        int[] result = new int[length()];
        for (int i = 0; i < result.length; i++) {
            result[i] = getInteger(i, 0);
        }

        return result;
    }

    public long[] toLongArray() {
        long[] result = new long[length()];
        for (int i = 0; i < result.length; i++) {
            result[i] = getLong(i, 0);
        }

        return result;
    }

    @Override
    public String renderJson() {

        StringBuilder result = new StringBuilder("[");
        String separator = "";
        for (int i = 0; i < objects.size(); i++) {
            result.append(separator).append(JsonRenderer.toJson(objects.get(i)));
            separator = ",";
        }
        result.append("]");

        return result.toString();
    }
}
