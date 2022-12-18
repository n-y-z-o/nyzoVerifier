package co.nyzo.verifier.nyzoScript.scripts;

import co.nyzo.verifier.*;
import co.nyzo.verifier.json.*;
import co.nyzo.verifier.nyzoScript.*;
import co.nyzo.verifier.util.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GraffitiScript implements NyzoScript {

    // This is the first NyzoScript. It was created to implement the processing necessary for
    // http://tech.nyzo.co/micropay/graffitiExample.

    // We plan to implement a mechanism with which NyzoScript implementations can be registered on clients. The
    // hard-coded implementation will serve as a testbed for this script mechanism.

    private static final int width = 288;
    private static final int height = 45;

    private static final String colorsJsonKey = "colors";
    private static final String amountsJsonKey = "amounts";

    @Override
    public NyzoScriptState update(NyzoScriptState inputState, List<Transaction> transactions) {

        // Get the existing data from the input state.
        int[] colors = new int[0];
        long[] amounts = new long[0];
        if (inputState != null) {
            try {
                JsonObject inputData = (JsonObject) Json.parse(new String(inputState.getData(),
                        StandardCharsets.UTF_8));
                colors = ((JsonArray) inputData.get(colorsJsonKey)).toIntegerArray();
                amounts = ((JsonArray) inputData.get(amountsJsonKey)).toLongArray();
            } catch (Exception e) {
                LogUtil.println("exception processing input state: " + PrintUtil.printException(e));
            }
        }

        // Ensure the arrays are the correct lengths.
        int expectedLength = width * height;
        if (colors.length != expectedLength) {
            int[] resizedColors = new int[expectedLength];
            System.arraycopy(colors, 0, resizedColors, 0, Math.min(colors.length, expectedLength));
            colors = resizedColors;
        }
        if (amounts.length != expectedLength) {
            long[] resizedAmounts = new long[expectedLength];
            System.arraycopy(amounts, 0, resizedAmounts, 0, Math.min(amounts.length, expectedLength));
            amounts = resizedAmounts;
        }

        // Write the pixels from each transaction to the arrays.
        int bitsPerCoordinate = (int) Math.ceil(Math.log(width * height) / Math.log(2.0));
        int bitsPerPixel = Math.max(8, bitsPerCoordinate + 4);  // minimum of 1 byte (8 bits) per pixel
        for (Transaction transaction : transactions) {
            String bitString = bitStringForSenderData(transaction.getSenderData());
            int numberOfPixels = bitString.length() / bitsPerPixel;
            for (int i = 0; i < numberOfPixels; i++) {
                String indexString = bitString.substring(i * bitsPerPixel, i * bitsPerPixel + bitsPerCoordinate);
                String colorString = bitString.substring(i * bitsPerPixel + bitsPerCoordinate, (i + 1) * bitsPerPixel);

                int index = Integer.parseUnsignedInt(indexString, 2);
                int color = Integer.parseUnsignedInt(colorString, 2);

                if (index < width * height && transaction.getAmount() >= amounts[index] * 2 && color >= 0 &&
                        color <= 15) {
                    colors[index] = color;
                    amounts[index] = transaction.getAmount();
                }
            }
        }

        // Store the image, amounts, width, and height in the output data.
        Map<String, Object> outputMap = new HashMap<>();
        outputMap.put("colors", colors);
        outputMap.put("amounts", amounts);
        outputMap.put("width", width);
        outputMap.put("height", height);
        JsonObject outputData = new JsonObject(outputMap);

        // Return a state with data type and output data.
        return new NyzoScriptState(NyzoScriptStateContentType.Json,
                outputData.renderJson().getBytes(StandardCharsets.UTF_8));
    }

    private static String bitStringForSenderData(byte[] senderData) {
        StringBuilder bitString = new StringBuilder();
        for (byte value : senderData) {
            String bitStringForByte = Integer.toBinaryString(value & 0xff);
            int padZeros = 8 - bitStringForByte.length();
            for (int j = 0; j < padZeros; j++) {
                bitString.append("0");
            }
            bitString.append(bitStringForByte);
        }

        return bitString.toString();
    }
}
