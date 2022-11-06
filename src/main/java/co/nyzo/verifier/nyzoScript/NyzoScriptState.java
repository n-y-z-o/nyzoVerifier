package co.nyzo.verifier.nyzoScript;

import co.nyzo.verifier.*;
import co.nyzo.verifier.json.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class NyzoScriptState implements JsonRenderable {

    private static final String creationHeightKey = "creationHeight";
    private static final String lastUpdateHeightKey = "lastUpdateHeight";
    private static final String contentTypeKey = "contentType";
    private static final String containsUnconfirmedDataKey = "containsUnconfirmedData";
    private static final String dataKey = "data";

    private final long creationHeight;
    private final long lastUpdateHeight;
    private final NyzoScriptStateContentType contentType;
    private final boolean containsUnconfirmedData;
    private final byte[] data;

    public NyzoScriptState(NyzoScriptStateContentType contentType, byte[] data) {
        this.creationHeight = -1L;
        this.lastUpdateHeight = -1L;
        this.contentType = contentType;
        this.containsUnconfirmedData = true;
        this.data = data;
    }

    public NyzoScriptState(long creationHeight, long lastUpdateHeight, NyzoScriptStateContentType contentType,
                           boolean containsUnconfirmedData, byte[] data) {
        this.creationHeight = creationHeight;
        this.lastUpdateHeight = lastUpdateHeight;
        this.contentType = contentType;
        this.containsUnconfirmedData = containsUnconfirmedData;
        this.data = data;
    }

    public long getCreationHeight() {
        return creationHeight;
    }

    public long getLastUpdateHeight() {
        return lastUpdateHeight;
    }

    public NyzoScriptStateContentType getContentType() {
        return contentType;
    }

    public byte[] getData() {
        return data;
    }

    public boolean containsUnconfirmedData() {
        return containsUnconfirmedData;
    }

    @Override
    public String renderJson() {
        StringBuilder result = new StringBuilder("{\"").append(creationHeightKey).append("\":").append(creationHeight);
        result.append(",\"").append(lastUpdateHeightKey).append("\":").append(lastUpdateHeight);
        result.append(",\"").append(contentTypeKey).append("\":").append(contentType.getValue());
        result.append(",\"").append(containsUnconfirmedDataKey).append("\":").append(containsUnconfirmedData);
        result.append(",\"").append(dataKey).append("\":");
        if (contentType == NyzoScriptStateContentType.Binary) {
            result.append("\"").append(renderDataArrayJson()).append("\"");
        } else if (contentType == NyzoScriptStateContentType.Json) {
            result.append(renderDataArrayJson());
        }
        result.append("}");

        return result.toString();
    }

    private String renderDataArrayJson() {

        String result;
        if (contentType == NyzoScriptStateContentType.Binary) {
            result = Base64.getEncoder().encodeToString(data);
        } else if (contentType == NyzoScriptStateContentType.Json) {
            result = new String(data, StandardCharsets.UTF_8);
        } else {  // contentType == NyzoScriptStateContentType.Unknown
            result = ByteUtil.arrayAsStringNoDashes(data);
        }

        return result;
    }

    public static NyzoScriptState fromJsonString(String jsonString) {

        Object object = Json.parse(jsonString);
        NyzoScriptState state = null;
        if (object instanceof JsonObject) {
            JsonObject jsonObject = (JsonObject) object;
            long creationHeight = jsonObject.getLong(creationHeightKey, -1);
            long lastUpdateHeight = jsonObject.getLong(lastUpdateHeightKey, -1);
            NyzoScriptStateContentType contentType =
                    NyzoScriptStateContentType.forValue(jsonObject.getInteger(contentTypeKey, -1));
            boolean containsUnconfirmedData = jsonObject.getBoolean(containsUnconfirmedDataKey, false);
            byte[] data = null;
            if (contentType == NyzoScriptStateContentType.Json) {
                data = JsonRenderer.toJson(jsonObject.get(dataKey)).getBytes(StandardCharsets.UTF_8);
            } else if (contentType == NyzoScriptStateContentType.Binary) {
                data = Base64.getDecoder().decode(jsonObject.getString(dataKey, ""));
            }

            if (creationHeight > 0 && lastUpdateHeight >= creationHeight &&
                    contentType != NyzoScriptStateContentType.Unknown && data != null) {
                state = new NyzoScriptState(creationHeight, lastUpdateHeight, contentType, containsUnconfirmedData,
                        data);
            }
        }

        return state;
    }
}
