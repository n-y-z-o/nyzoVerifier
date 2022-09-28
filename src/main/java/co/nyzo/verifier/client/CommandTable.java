package co.nyzo.verifier.client;

import co.nyzo.verifier.json.JsonRenderable;
import co.nyzo.verifier.json.JsonRenderer;

import java.util.ArrayList;
import java.util.List;

public class CommandTable implements JsonRenderable {

    private CommandTableHeader[] headers;
    private List<Object[]> rows;
    private boolean invertedRowsColumns;

    public CommandTable(CommandTableHeader... headers) {
        this.headers = headers;
        this.rows = new ArrayList<>();
        this.invertedRowsColumns = false;
    }

    public void addRow(Object... values) {
        rows.add(values);
    }

    public CommandTableHeader[] getHeaders() {
        return headers;
    }

    public List<Object[]> getRows() {
        return rows;
    }

    public boolean isInvertedRowsColumns() {
        return invertedRowsColumns;
    }

    public void setInvertedRowsColumns(boolean invertedRowsColumns) {
        this.invertedRowsColumns = invertedRowsColumns;
    }

    public String renderJson() {

        StringBuilder result = new StringBuilder("[");
        String rowSeparator = "";
        CommandTableHeader[] headers = getHeaders();
        for (Object[] row : getRows()) {
            result.append(rowSeparator).append("{");
            rowSeparator = ",";
            int length = Math.min(row.length, headers.length);
            for (int i = 0; i < length; i++) {
                result.append(i == 0 ? "" : ",").append("\"").append(headers[i].getIdentifier()).append("\":")
                        .append(JsonRenderer.toJson(row[i]));
            }
            result.append("}");
        }
        result.append("]");

        return result.toString();
    }
}
