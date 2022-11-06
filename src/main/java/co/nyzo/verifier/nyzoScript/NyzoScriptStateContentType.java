package co.nyzo.verifier.nyzoScript;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public enum NyzoScriptStateContentType {

    Binary((byte) 0),
    Json((byte) 1),
    Unknown((byte) -1);

    private static final Map<Integer, NyzoScriptStateContentType> contentTypeMap = new ConcurrentHashMap<>();
    static {
        for (NyzoScriptStateContentType contentType : values()) {
            contentTypeMap.put(contentType.getValue(), contentType);
        }
    }

    private final int value;

    NyzoScriptStateContentType(byte value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static NyzoScriptStateContentType forValue(int value) {
        return contentTypeMap.getOrDefault(value, Unknown);
    }
}
