package co.nyzo.verifier.client;

import java.util.*;

public class ConsoleUtil {

    public static void printTable(CommandOutput output, String... values) {

        List<List<String>> columns = new ArrayList<>();
        for (String value : values) {
            columns.add(Collections.singletonList(value));
        }

        printTable(columns, output);
    }

    public static void printTable(CommandTable table, CommandOutput output) {

        if (table != null) {
            if (table.isInvertedRowsColumns()) {
                // This is the case when the header is on the left side of the table. Start by adding the header as the
                // first column.
                List<List<String>> columns = new ArrayList<>();
                List<String> headerColumn = new ArrayList<>();
                columns.add(headerColumn);
                for (CommandTableHeader header : table.getHeaders()) {
                    headerColumn.add(header.getLabel());
                }

                // Add another column for every other row.
                for (Object[] row : table.getRows()) {
                    List<String> column = new ArrayList<>();
                    for (Object object : row) {
                        column.add(object + "");
                    }
                    columns.add(column);
                }

                // Print the table.
                printTable(columns, output);
            } else {
                // This is the case when the header is on the top of the table.
                List<List<String>> columns = new ArrayList<>();

                // Add a column for each header.
                for (CommandTableHeader header : table.getHeaders()) {
                    List<String> column = new ArrayList<>();
                    columns.add(column);
                    column.add(header.getLabel());
                }

                // Add cells for each value.
                for (Object[] row : table.getRows()) {
                    for (int i = 0; i < Math.min(row.length, columns.size()); i++) {
                        columns.get(i).add(row[i] + "");
                    }
                }

                // Print the table.
                printTable(columns, new HashSet<>(Collections.singleton(0)), output);
            }
        }
    }

    public static void printTable(List<List<String>> columns, CommandOutput output) {

        printTable(columns, new HashSet<>(), output);
    }

    public static void printTable(List<List<String>> columns, Set<Integer> dividerRows, CommandOutput output) {

        int numberOfColumns = columns.size();
        int numberOfRows = 0;
        int[] columnWidths = new int[numberOfColumns];
        for (int j = 0; j < numberOfColumns; j++) {
            List<String> column = columns.get(j);
            numberOfRows = Math.max(numberOfRows, column.size());
            for (String value : column) {
                columnWidths[j] = Math.max(columnWidths[j], length(value));
            }
            columnWidths[j] += 2;  // left and right padding
        }

        // Print the top border.
        output.print("╔");
        for (int j = 0; j < numberOfColumns; j++) {
            for (int i = 0; i < columnWidths[j]; i++) {
                output.print("═");
            }
            if (j == numberOfColumns - 1) {
                output.println("╗");
            } else {
                output.print("╦");
            }
        }

        // Print the rows.
        for (int j = 0; j < numberOfRows; j++) {

            for (int i = 0; i < numberOfColumns; i++) {
                output.print("║ ");
                List<String> column = columns.get(i);
                String value = j < column.size() ? column.get(j) : "";
                output.print(value);
                for (int k = 0; k < columnWidths[i] - length(value) - 1; k++) {
                    output.print(" ");
                }
            }
            output.println("║");

            // Print the divider row.
            if (dividerRows.contains(j)) {
                output.print("╠");
                for (int i = 0; i < numberOfColumns; i++) {
                    for (int k = 0; k < columnWidths[i]; k++) {
                        output.print("═");
                    }
                    if (i == numberOfColumns - 1) {
                        output.println("╣");
                    } else {
                        output.print("╬");
                    }
                }
            }
        }

        // Print the bottom border.
        output.print("╚");
        for (int j = 0; j < numberOfColumns; j++) {
            for (int i = 0; i < columnWidths[j]; i++) {
                output.print("═");
            }
            if (j == numberOfColumns - 1) {
                output.println("╝");
            } else {
                output.print("╩");
            }
        }
    }

    private static int length(String value) {
        // Remove color codes from the length calculation.
        return value.replaceAll("\u001B\\[[;\\d]*m", "").length();
    }

    public static void printMessages(List<String> lines, String label, ConsoleColor color, CommandOutput output) {

        if (lines != null && !lines.isEmpty()) {

            // Determine the label length and make a blank version of the label.
            int labelLength = label.length();
            String blankLabel = blankString(labelLength);

            // Print the lines, wrapping each line to try to improve readability.
            System.out.println();
            for (String line : lines) {
                List<String> wrappedLines = wrapAndPad(line, 80 - labelLength);
                String prefix = label;
                for (String wrappedLine : wrappedLines) {
                    output.println(ConsoleColor.Yellow.background() + prefix + wrappedLine + ConsoleColor.reset);
                    prefix = blankLabel;
                }
                System.out.println();
            }
        }
    }

    private static List<String> wrapAndPad(String line, int wrapLength) {

        // Produce a list of wrapped lines.
        String[] split = line.split(" ");
        List<String> wrappedLines = new ArrayList<>();
        if (split.length > 0) {
            int maximumLength = 0;
            StringBuilder lineBuilder = new StringBuilder(split[0]);
            for (int i = 1; i < split.length; i++) {
                if (lineBuilder.length() + split[i].length() + 1 > wrapLength) {
                    maximumLength = Math.max(lineBuilder.length(), maximumLength);
                    wrappedLines.add(lineBuilder.toString());
                    lineBuilder = new StringBuilder(split[i]);
                } else {
                    lineBuilder.append(' ').append(split[i]);
                }
            }

            // Add the last line.
            maximumLength = Math.max(lineBuilder.length(), maximumLength);
            wrappedLines.add(lineBuilder.toString());

            // Pad all the lines in the list to be a consistent width.
            for (int i = 0; i < wrappedLines.size(); i++) {
                int padLength = maximumLength - wrappedLines.get(i).length();
                wrappedLines.set(i, wrappedLines.get(i) + blankString(padLength));
            }
        }

        return wrappedLines;
    }

    private static String blankString(int length) {

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < length; i++) {
            result.append(' ');
        }

        return result.toString();
    }
}
