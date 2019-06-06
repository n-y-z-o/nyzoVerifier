package co.nyzo.verifier.client;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConsoleUtil {

    public static void printTable(String message) {

        printTable(Collections.singletonList(Collections.singletonList(message)));
    }

    public static void printTable(List<List<String>> columns) {

        printTable(columns, new HashSet<>());
    }

    public static void printTable(List<List<String>> columns, Set<Integer> dividerRows) {

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
        System.out.print("╔");
        for (int j = 0; j < numberOfColumns; j++) {
            for (int i = 0; i < columnWidths[j]; i++) {
                System.out.print("═");
            }
            if (j == numberOfColumns - 1) {
                System.out.println("╗");
            } else {
                System.out.print("╦");
            }
        }

        // Print the rows.
        for (int j = 0; j < numberOfRows; j++) {

            for (int i = 0; i < numberOfColumns; i++) {
                System.out.print("║ ");
                List<String> column = columns.get(i);
                String value = j < column.size() ? column.get(j) : "";
                System.out.print(value);
                for (int k = 0; k < columnWidths[i] - length(value) - 1; k++) {
                    System.out.print(" ");
                }
            }
            System.out.println("║");

            // Print the divider row.
            if (dividerRows.contains(j)) {
                System.out.print("╠");
                for (int i = 0; i < numberOfColumns; i++) {
                    for (int k = 0; k < columnWidths[i]; k++) {
                        System.out.print("═");
                    }
                    if (i == numberOfColumns - 1) {
                        System.out.println("╣");
                    } else {
                        System.out.print("╬");
                    }
                }
            }
        }

        // Print the bottom border.
        System.out.print("╚");
        for (int j = 0; j < numberOfColumns; j++) {
            for (int i = 0; i < columnWidths[j]; i++) {
                System.out.print("═");
            }
            if (j == numberOfColumns - 1) {
                System.out.println("╝");
            } else {
                System.out.print("╩");
            }
        }
    }

    private static int length(String value) {
        // Remove color codes from the length calculation.
        return value.replaceAll("\u001B\\[[;\\d]*m", "").length();
    }
}
