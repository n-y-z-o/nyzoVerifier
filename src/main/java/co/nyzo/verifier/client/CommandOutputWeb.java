package co.nyzo.verifier.client;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CommandOutputWeb implements CommandOutput {

    private static final int maximumMapSize = 1000;

    private String identifier;
    private Map<Integer, String> output;
    private AtomicInteger outputLineIndex;
    private boolean complete;
    private String textColor;
    private String backgroundColor;

    private static final Random random = new Random();

    public CommandOutputWeb() {

        this.identifier = String.format("%09d", random.nextInt(1_000_000_000));
        this.output = new ConcurrentHashMap<>();
        this.outputLineIndex = new AtomicInteger(0);
        this.complete = false;
        this.textColor = "";
        this.backgroundColor = "";
    }

    public String getIdentifier() {
        return identifier;
    }

    public void print(String string) {
        int index = outputLineIndex.get();
        output.put(index, output.getOrDefault(index, "") + string);
    }

    public void println(String string) {
        // Add the specified line. Also, blindly remove an item at the trailing index to limit the map size.
        int index = outputLineIndex.getAndIncrement();
        output.put(index, output.getOrDefault(index, "") + string);
        output.remove(index - maximumMapSize);
    }

    public List<String> getOutput() {

        // Determine the minimum index in the map.
        int minimumIndex = outputLineIndex.get();
        for (Integer index : output.keySet()) {
            minimumIndex = Math.min(minimumIndex, index);
        }

        // Provide all output, ordered by index, between the minimum and maximum indices.
        List<String> result = new ArrayList<>();
        int maximumIndex = outputLineIndex.get() - 1;
        for (int i = minimumIndex; i <= maximumIndex; i++) {
            String line = output.remove(i);
            if (line != null) {
                result.add(replaceColorCodes(line));
            }
        }

        // If the stream has been marked as complete and the output buffer is empty, unregister this object from the
        // manager.
        if (complete && output.isEmpty()) {
            CommandOutputWebManager.unregister(this);
        }

        return result;
    }

    public void setComplete() {
        // Increment the index to ensure the last line can be retrieved.
        outputLineIndex.incrementAndGet();

        // Mark that this output is complete.
        this.complete = true;
    }

    private String replaceColorCodes(String string) {

        // Replace color codes with HTML spans. The colors may persist from a previous line, and the expectation is that
        // all lines will be processed in order.
        StringBuilder result = new StringBuilder();
        char[] characters = string.toCharArray();
        StringBuilder section = new StringBuilder(openSpan());
        for (int i = 0; i < characters.length; i++) {
            // Check the character. If it is the control code (27), process and start a new span.
            char character = characters[i];
            if (character == 27) {
                // Append the previous section of text to the result. If the previous span color or background color is
                // set, close the span.
                result.append(section).append(closeSpan());

                // Determine the color code that this marker represents.
                char firstDigit = i + 2 < characters.length ? characters[i + 2] : '0';
                if (firstDigit == '0') {
                    // This is the reset code.
                    textColor = "";
                    backgroundColor = "";

                    // Advance past the rest of the code.
                    i += 3;
                } else {
                    // Color codes in the 9x range are bright text, and color codes in the 10x range are bright
                    // backgrounds.
                    boolean isBright = firstDigit == '9' || firstDigit == '1';

                    // Color codes in the 4x range are normal backgrounds, and color codes in the 10x range are bright
                    // backgrounds.
                    boolean isBackground = firstDigit == '4' || firstDigit == '1';

                    // Get the color specifier.
                    char colorDigit = firstDigit == '1' ? (i + 4 < characters.length ? characters[i + 4] : '0') :
                            (i + 3 < characters.length ? characters[i + 3] : '0');

                    // Set the appropriate color.
                    if (isBackground) {
                        backgroundColor = colorForCode(colorDigit, isBright);
                    } else {
                        textColor = colorForCode(colorDigit, isBright);
                    }

                    // Advance past the rest of the code.
                    i += firstDigit == '1' ? 5 : 4;
                }

                // Reset the section.
                section = new StringBuilder(openSpan());
            } else {
                section.append(character);
            }
        }

        // If the section is non-empty, append it to the result. Close the span for non-empty colors.
        if (section.length() > 0) {
            result.append(section).append(closeSpan());
        }

        return result.toString();
    }

    private String openSpan() {
        StringBuilder result = new StringBuilder();
        if (!textColor.isEmpty() || !backgroundColor.isEmpty()) {
             result.append("<span style=\"");
             String separator = "";
             if (!textColor.isEmpty()) {
                 result.append("color: ").append(textColor).append(";");
                 separator = " ";
             }
             if (!backgroundColor.isEmpty()) {
                 result.append(separator).append("background-color: ").append(backgroundColor).append(";");
             }
             result.append("\">");
        }

        return result.toString();
    }

    private String closeSpan() {
        return textColor.isEmpty() && backgroundColor.isEmpty() ? "" : "</span>";
    }

    private String colorForCode(char colorDigit, boolean isBright) {

        String result = "";  // 0 is black, which we do not explicitly specify in the span
        switch (colorDigit) {
            case '1':  // red
                result = isBright ? "#f00" : "#a00";
                break;
            case '2':  // green
                result = isBright ? "#0f0" : "#0a0";
                break;
            case '3':  // yellow
                result = isBright ? "#ff0" : "#aa0";
                break;
            case '4':  // blue
                result = isBright ? "#00f" : "#00a";
                break;
            case '5':  // magenta
                result = isBright ? "#f0f" : "#a0a";
                break;
            case '6':  // cyan
                result = isBright ? "#0ff" : "#0aa";
                break;
            case '7':  // white
                result = isBright ? "#fff" : "#aaa";
                break;
        }

        return result;
    }
}
