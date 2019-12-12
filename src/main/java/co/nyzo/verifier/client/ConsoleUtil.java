package co.nyzo.verifier.client;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConsoleUtil {

    public static void printTable(String message, CommandOutput output) {

        printTable(Collections.singletonList(Collections.singletonList(message)), output);
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
}
