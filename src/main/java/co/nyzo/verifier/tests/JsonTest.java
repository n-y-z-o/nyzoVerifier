package co.nyzo.verifier.tests;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.CommandTable;
import co.nyzo.verifier.client.ExecutionResult;
import co.nyzo.verifier.client.commands.FrozenEdgeCommand;
import co.nyzo.verifier.json.Json;
import co.nyzo.verifier.json.JsonArray;
import co.nyzo.verifier.json.JsonObject;
import co.nyzo.verifier.json.JsonRenderer;
import co.nyzo.verifier.util.PrintUtil;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class JsonTest implements NyzoTest {
    private String failureCause = null;

    public static void main(String[] args) {

        RunMode.setRunMode(RunMode.Test);
        JsonTest test = new JsonTest();
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
                successful = testFrozenEdgeCommand();
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

        // Test a string that parses to a one key/value JsonObject.
        String inputString1 = "{\"stringValue\":\"hello, Nyzo!\"}";
        JsonObject parsed1 = (JsonObject) Json.parse(inputString1);
        if (!parsed1.get("stringValue").equals("hello, Nyzo!")) {
            successful = false;
            failureCause = "testDeserialization(): incorrect result for input string 1";
        }

        // Test a string that parses to a 5 key/value JsonObject. This was one of the first internal tests we created
        // for the Json class.
        if (successful) {
            String inputString2 = "{\"month_to_date_balance\":\"179.72\",\"account_balance\":\"0.00\"," +
                    "\"month_to_date_usage\":\"179.72\",\"generated_at\":\"2020-07-19T06:41:57Z\"," +
                    "\"supplementalTransactionValid\":true}";
            JsonObject parsed2 = (JsonObject) Json.parse(inputString2);
            String[] keys = { "month_to_date_balance", "account_balance", "month_to_date_usage", "generated_at",
                    "supplementalTransactionValid" };
            String[] expectedValues = { "179.72", "0.00", "179.72", "2020-07-19T06:41:57Z", "true" };
            for (int i = 0; i < keys.length; i++) {
                String actualValue = parsed2.getString(keys[i], "");
                if (!actualValue.equals(expectedValues[i])) {
                    failureCause = "testDeserialization(): for input string 2, index " + i + ", expected value of " +
                            expectedValues[i] + ", actual value of " + actualValue;
                }
            }
        }

        // Test a result from the client.
        if (successful) {
            String inputString3 = "{\"notices\":[],\"errors\":[],\"result\":[{\"blockHeight\":9115592," +
                    "\"senderIdBytes\":\"e3ca354bd6efe721-77dbc93237709bd7-f235d1641ebf7288-79666734a23595ab\"," +
                    "\"senderIdNyzoString\":\"id__8efadkMnZ~tyu.M9cAuND.wQdu5B7I.Qz7CDqRizdqnIot9_NT01\"," +
                    "\"receiverIdBytes\":\"c34a6f1942cb7ec1-0d2a440b3e116041-d05df2746ebe7b41-802340a1495e7af5\"," +
                    "\"receiverIdNyzoString\":\"id__8cdasPC2QVZ13iG42RWhp47gow9SsIXZgp0Aga59oEITG2X-M7Ur\"," +
                    "\"amount\":\"∩0.100000\",\"previousVerifierIdBytes\":null,\"previousVerifierIdNyzoString\":null," +
                    "\"expectedVerifierIdBytes\":null,\"expectedVerifierIdNyzoString\":null," +
                    "\"nextVerifierIdBytes\":null,\"nextVerifierIdNyzoString\":null,\"forwarded\":false," +
                    "\"previouslyForwarded\":true,\"processed\":false,\"age\":-11.622,\"senderBalance\":\"∩14.786025\"," +
                    "\"supplementalTransactionValid\":true}]}";
            JsonObject parsed3 = (JsonObject) Json.parse(inputString3);

            // Check the notices array.
            Object noticesArray = parsed3.get("notices");
            if (!(noticesArray instanceof JsonArray)) {
                successful = false;
                failureCause = "testDeserialization(): for input string 3, the notices array is missing or not the " +
                        "correct type: " + noticesArray;
            } else if (((JsonArray) noticesArray).length() != 0) {
                successful = false;
                failureCause = "testDeserialization(): for input string 3, the notices array is not empty: " +
                        ((JsonArray) noticesArray).length();
            }

            // Check the errors array.
            Object errorsArray = parsed3.get("errors");
            if (successful && !(errorsArray instanceof JsonArray)) {
                successful = false;
                failureCause = "testDeserialization(): for input string 3, the errors array is missing or not the " +
                        "correct type: " + errorsArray;
            } else if (successful && ((JsonArray) errorsArray).length() != 0) {
                successful = false;
                failureCause = "testDeserialization(): for input string 3, the errors array is not empty: " +
                        ((JsonArray) errorsArray).length();
            }

            // Check the result array.
            Object resultArray = parsed3.get("result");
            if (successful && !(resultArray instanceof JsonArray)) {
                successful  = false;
                failureCause = "testDeserialization(): for input string 3, the result array is missing or not the " +
                        "correct type: " + resultArray;
            } else if (successful && ((JsonArray) resultArray).length() != 1) {
                successful = false;
                failureCause = "testDeserialization(): for input string 3, the result array is not of length 1: " +
                        ((JsonArray) resultArray).length();
            } else if (successful) {
                Object resultObject = ((JsonArray) resultArray).get(0);
                if (!(resultObject instanceof JsonObject)) {
                    successful = false;
                    failureCause = "testDeserialization(): for input string 3, the result object is not the correct " +
                            "type";
                } else {
                    String[] keys = { "blockHeight", "senderIdBytes", "senderIdNyzoString", "receiverIdBytes",
                            "receiverIdNyzoString", "amount", "previousVerifierIdBytes", "previousVerifierIdNyzoString",
                            "expectedVerifierIdBytes", "expectedVerifierIdNyzoString", "nextVerifierIdBytes",
                            "nextVerifierIdNyzoString", "forwarded", "previouslyForwarded", "processed", "age",
                            "senderBalance", "supplementalTransactionValid" };
                    String[] expectedValues = { "9115592",
                            "e3ca354bd6efe721-77dbc93237709bd7-f235d1641ebf7288-79666734a23595ab",
                            "id__8efadkMnZ~tyu.M9cAuND.wQdu5B7I.Qz7CDqRizdqnIot9_NT01",
                            "c34a6f1942cb7ec1-0d2a440b3e116041-d05df2746ebe7b41-802340a1495e7af5",
                            "id__8cdasPC2QVZ13iG42RWhp47gow9SsIXZgp0Aga59oEITG2X-M7Ur",
                            "∩0.100000", "null", "null", "null", "null", "null", "null", "false", "true", "false",
                            "-11.622", "∩14.786025", "true" };
                    for (int i = 0; i < keys.length && successful; i++) {
                        Object actualValue = ((JsonObject) resultObject).get(keys[i]);
                        if (!expectedValues[i].equals(actualValue)) {
                            successful = false;
                            failureCause = "testDeserialization(): for input string 3, index " + i + ", expected " +
                                    "value of " + expectedValues[i] + ", actual value of " + actualValue;
                        }
                    }
                }
            }
        }

        // Test a string that parses to an array of numbers.
        if (successful) {
            String inputString4 = "[\"0\", 2, \"4\", 6, \"8\", 10]";
            JsonArray parsed4 = (JsonArray) Json.parse(inputString4);
            for (int i = 0; i < 6 && successful; i++) {
                double expectedValue = i * 2.0;
                double actualValue = parsed4.getDouble(i, -1.0);
                if (actualValue != expectedValue) {
                    successful = false;
                    failureCause = "testDeserialization(): for input string 4, index " + i + ", expected value of " +
                            expectedValue + ", actual value of " + actualValue;
                }
            }
        }

        return successful;
    }

    private boolean testFrozenEdgeCommand() {

        // Create and set a dummy frozen edge in the BlockManager.
        int blockchainVersion = 0;
        long height = 1_000_000_000L;
        byte[] previousBlockHash = ByteUtil.byteArrayFromHexString("aabbcc", FieldByteSize.hash);
        long startTimestamp = 10_000_000_000L;
        List<Transaction> transactions = new ArrayList<>();
        byte[] balanceListHash = ByteUtil.byteArrayFromHexString("ddeeff11", FieldByteSize.hash);
        Block block = new Block(blockchainVersion, height, previousBlockHash, startTimestamp, transactions,
                balanceListHash);

        // Use reflection to cleanly set the private frozenEdge field.
        boolean successful = true;
        try {
            Field frozenEdgeField = BlockManager.class.getDeclaredField("frozenEdge");
            frozenEdgeField.setAccessible(true);
            frozenEdgeField.set(BlockManager.class, block);
        } catch (Exception e) {
            successful = false;
            failureCause = "testFrozenEdgeCommand(): unable to set BlockManager.frozenEdge field, exception: " +
                    e.getMessage();
        }

        // Run the command, produce the JSON result, and parse that result into a JSON object.
        FrozenEdgeCommand command = new FrozenEdgeCommand();
        ExecutionResult result = command.run(new ArrayList<>(), null);
        String jsonString = new String(result.toEndpointResponse().getContent(), StandardCharsets.UTF_8);
        JsonObject jsonObject = (JsonObject) Json.parse(jsonString);

        // Check the notices array.
        Object noticesArray = jsonObject.get("notices");
        if (successful && !(noticesArray instanceof JsonArray)) {
            successful = false;
            failureCause = "testFrozenEdgeCommand(): the notices array is missing or not the correct type: " +
                    noticesArray;
        } else if (successful && ((JsonArray) noticesArray).length() != 0) {
            successful = false;
            failureCause = "testFrozenEdgeCommand(): the notices array is not empty: " +
                    ((JsonArray) noticesArray).length();
        }

        // Check the errors array.
        Object errorsArray = jsonObject.get("errors");
        if (successful && !(errorsArray instanceof JsonArray)) {
            successful = false;
            failureCause = "testFrozenEdgeCommand(): the errors array is missing or not the correct type: " +
                    errorsArray;
        } else if (successful && ((JsonArray) errorsArray).length() != 0) {
            successful = false;
            failureCause = "testFrozenEdgeCommand(): the errors array is not empty: " +
                    ((JsonArray) errorsArray).length();
        }

        // Check the result object.
        Object resultArray = jsonObject.get("result");
        if (successful && !(resultArray instanceof JsonArray)) {
            successful  = false;
            failureCause = "testFrozenEdgeCommand(): the result array is missing or not the correct type: " +
                    resultArray;
        } else if (successful && ((JsonArray) resultArray).length() != 1) {
            successful = false;
            failureCause = "testFrozenEdgeCommand(): the result array is not of length 1: " +
                    ((JsonArray) resultArray).length();
        } else if (successful) {
            Object resultObject = ((JsonArray) resultArray).get(0);
            if (!(resultObject instanceof JsonObject)) {
                successful = false;
                failureCause = "testFrozenEdgeCommand(): the result object is not the correct type";
            } else {
                String[] keys = { "height", "hash", "verificationTimestampMilliseconds", "distanceFromOpenEdge",
                        "clientVersion" };
                String[] expectedValues = { block.getBlockHeight() + "",
                        ByteUtil.arrayAsStringWithDashes(block.getHash()), block.getVerificationTimestamp() + "",
                        (block.getBlockHeight() * -1L - 1L) + "", Version.getVersion() + "" };
                for (int i = 0; i < keys.length && successful; i++) {
                    Object actualValue = ((JsonObject) resultObject).get(keys[i]);
                    if (!expectedValues[i].equals(actualValue)) {
                        successful = false;
                        failureCause = "testFrozenEdgeCommand(): index " + i + ", expected value of " +
                                expectedValues[i] + ", actual value of " + actualValue;
                    }
                }
            }
        }

        return successful;
    }
}
