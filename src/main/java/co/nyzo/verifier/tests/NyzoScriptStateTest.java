package co.nyzo.verifier.tests;

import co.nyzo.verifier.*;
import co.nyzo.verifier.json.*;
import co.nyzo.verifier.nyzoScript.*;
import co.nyzo.verifier.util.PrintUtil;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

public class NyzoScriptStateTest implements NyzoTest {
    private String failureCause = null;

    public static void main(String[] args) {

        RunMode.setRunMode(RunMode.Test);
        NyzoScriptStateTest test = new NyzoScriptStateTest();
        boolean successful = test.run();

        if (!successful) {
            System.out.println(TestUtil.failureCause(test.getFailureCause()));
        }
    }

    public boolean run() {

        boolean successful;
        try {
            successful = testDeserialization();

            if (successful) {
                successful = testSerialization();
            }

        } catch (Exception e) {
            failureCause = "exception in " + getClass().getSimpleName() + ": " + PrintUtil.printException(e);
            successful = false;
        }

        System.out.println(TestUtil.passFail(successful));

        return successful;
    }

    public String getFailureCause() {
        return failureCause;
    }

    private boolean testDeserialization() {

        boolean successful = true;

        String[] stateStrings = {
                "{\"creationHeight\":1,\"lastUpdateHeight\":1,\"contentType\":1,\"containsUnconfirmedData\":false," +
                        "\"data\":{}}",  // 0
                "{\"creationHeight\":0,\"lastUpdateHeight\":1,\"contentType\":1,\"containsUnconfirmedData\":false," +
                        "\"data\":{}}",  // 1
                "{\"creationHeight\":1,\"lastUpdateHeight\":1,\"contentType\":0,\"containsUnconfirmedData\":false," +
                        "\"data\":\"AAEC\"}",  // 2
        };

        NyzoScriptState[] expectedStates = {
                new NyzoScriptState(1, 1, NyzoScriptStateContentType.Json, false,
                        "{}".getBytes(StandardCharsets.UTF_8)),  // 0
                null,  // 1
                new NyzoScriptState(1, 1, NyzoScriptStateContentType.Binary, false,
                        ByteUtil.byteArrayFromHexString("000102", 3)),  // 2
        };

        for (int i = 0; i < stateStrings.length && successful; i++) {
            NyzoScriptState state = NyzoScriptState.fromJsonString(stateStrings[i]);
            NyzoScriptState expectedState = expectedStates[i];
            if (state == null && expectedState != null) {
                successful = false;
                failureCause = "iteration " + i + " of NyzoScriptStateTest.testDeserialization(), deserialized state " +
                        "unexpectedly null";
            } else if (state != null && expectedState == null) {
                successful = false;
                failureCause = "iteration " + i + " of NyzoScriptStateTest.testDeserialization(), deserialized state " +
                        "unexpectedly non-null";
            } else if (state != null) {
                if (state.getCreationHeight() != expectedState.getCreationHeight()) {
                    successful = false;
                    failureCause = "iteration " + i + " of NyzoScriptStateTest.testDeserialization(), expected " +
                            "creation height: " + expectedState.getCreationHeight() + ", actual creation height: " +
                            state.getCreationHeight();
                } else if (state.getLastUpdateHeight() != expectedState.getLastUpdateHeight()) {
                    successful = false;
                    failureCause = "iteration " + i + " of NyzoScriptStateTest.testDeserialization(), expected " +
                            "last-update height: " + expectedState.getLastUpdateHeight() +
                            ", actual last-update height: " + state.getLastUpdateHeight();
                } else if (state.getContentType() != expectedState.getContentType()) {
                    successful = false;
                    failureCause = "iteration " + i + " of NyzoScriptStateTest.testDeserialization(), expected " +
                            "content type: " + expectedState.getContentType() + ", actual content type: " +
                            state.getContentType();
                } else if (!ByteUtil.arraysAreEqual(state.getData(), expectedState.getData())) {
                    successful = false;
                    failureCause = "iteration " + i + " of NyzoScriptStateTest.testDeserialization(), expected data: " +
                            ByteUtil.arrayAsStringWithDashes(expectedState.getData()) + ", actual data: " +
                            ByteUtil.arrayAsStringWithDashes(state.getData());
                }
            }
        }


        return successful;
    }

    private boolean testSerialization() {

        boolean successful = true;

        NyzoScriptState[] states = {
                new NyzoScriptState(1, 1, NyzoScriptStateContentType.Json, false,
                        "{\"customField\":\"customValue\"}".getBytes(StandardCharsets.UTF_8)),  // 0
                new NyzoScriptState(1, 1, NyzoScriptStateContentType.Binary, false,
                        ByteUtil.byteArrayFromHexString("0001020304FEFF", 7)),  // 1
        };

        // Use reflection to cleanly set the private frozenEdgeHeight field so the value is predictable.
        long frozenEdgeHeight = 10L;
        try {
            Field frozenEdgeHeightField = BlockManager.class.getDeclaredField("frozenEdgeHeight");
            frozenEdgeHeightField.setAccessible(true);
            frozenEdgeHeightField.set(BlockManager.class, frozenEdgeHeight);
        } catch (Exception e) {
            successful = false;
            failureCause = "NyzoScriptStateTest.testSerialization(): unable to set BlockManager.frozenEdgeHeight " +
                    "field, exception: " + e.getMessage();
        }

        String[] expectedStateStrings = {
                "{\"creationHeight\":1,\"lastUpdateHeight\":1,\"frozenEdgeHeight\":" + frozenEdgeHeight +
                        ",\"contentType\":1,\"containsUnconfirmedData\":false," +
                        "\"data\":{\"customField\":\"customValue\"}}",  // 0
                "{\"creationHeight\":1,\"lastUpdateHeight\":1,\"frozenEdgeHeight\":" + frozenEdgeHeight +
                        ",\"contentType\":0,\"containsUnconfirmedData\":false,\"data\":\"AAECAwT+/w==\"}",  // 2
        };

        for (int i = 0; i < states.length && successful; i++) {
            String stateString = states[i].renderJson();
            String expectedStateString = expectedStateStrings[i];

            // Check the string representations. Because the NyzoScriptState uses a custom JSON renderer, ordering is
            // predictable.
            if (!stateString.equals(expectedStateString)) {
                successful = false;
                failureCause = "iteration " + i + " of NyzoScriptStateTest.testSerialization(), expected state " +
                        "string: " + expectedStateString + ", actual state string: " + stateString;
            }

            // Parse both strings to JSON objects.
            JsonObject object = (JsonObject) Json.parse(stateString);
            JsonObject expectedObject = (JsonObject) Json.parse(expectedStateString);

            // Check all properties.
            if (successful && object.getLong("creationHeight", -1) != expectedObject.getLong("creationHeight", -2)) {
                successful = false;
                failureCause = "iteration " + i + " of NyzoScriptStateTest.testSerialization(), expected " +
                        "creation height: " + expectedObject.getLong("creationHeight", -2) +
                        ", actual creation height: " + object.getLong("creationHeight", -1);
            } else if (object.getLong("lastUpdateHeight", -1) != expectedObject.getLong("lastUpdateHeight", -2)) {
                successful = false;
                failureCause = "iteration " + i + " of NyzoScriptStateTest.testSerialization(), expected " +
                        "last-update height: " + expectedObject.getLong("lastUpdateHeight", -2) +
                        ", actual last-update height: " + object.getLong("lastUpdateHeight", -1);
            } else if (object.getInteger("contentType", -1) != expectedObject.getInteger("contentType", -2)) {
                successful = false;
                failureCause = "iteration " + i + " of NyzoScriptStateTest.testSerialization(), expected " +
                        "content type: " + expectedObject.getInteger("contentType", -2) + ", actual content type: " +
                        object.getInteger("contentType", -1);
            } else if (!JsonRenderer.toJson(object.get("data"))
                    .equals(JsonRenderer.toJson(expectedObject.get("data")))) {
                successful = false;
                failureCause = "iteration " + i + " of NyzoScriptStateTest.testSerialization(), expected data: " +
                        JsonRenderer.toJson(expectedObject.get("data")) + ", actual data: " +
                        JsonRenderer.toJson(object.get("data"));
            }
        }

        return successful;
    }
}
