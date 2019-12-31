package co.nyzo.verifier.client;

import java.util.ArrayList;
import java.util.List;

public class CommandTable {

    private CommandTableHeader[] headers;
    private List<String[]> rows;
    private boolean invertedRowsColumns;

    public CommandTable(CommandTableHeader... headers) {
        this.headers = headers;
        this.rows = new ArrayList<>();
        this.invertedRowsColumns = false;
    }

    public void addRow(String... values) {
        rows.add(values);
    }

    public CommandTableHeader[] getHeaders() {
        return headers;
    }

    public List<String[]> getRows() {
        return rows;
    }

    public boolean isInvertedRowsColumns() {
        return invertedRowsColumns;
    }

    public void setInvertedRowsColumns(boolean invertedRowsColumns) {
        this.invertedRowsColumns = invertedRowsColumns;
    }
}
