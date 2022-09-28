package co.nyzo.verifier.client;

import co.nyzo.verifier.json.JsonRenderer;
import co.nyzo.verifier.web.EndpointResponse;
import co.nyzo.verifier.web.WebUtil;
import co.nyzo.verifier.web.elements.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SimpleExecutionResult implements ExecutionResult {

    private CommandTable[] result;
    private List<String> notices;
    private List<String> errors;

    public SimpleExecutionResult(List<String> notices, List<String> errors, CommandTable... result) {
        // For consistency in the JSON response, ensure that none of the fields are null.
        this.notices = notices == null ? new ArrayList<>() : notices;
        this.errors = errors == null ? new ArrayList<>() : errors;
        this.result = result == null ? new CommandTable[0] : result;
    }

    public CommandTable[] getResult() {
        return result;
    }

    public List<String> getNotices() {
        return notices;
    }

    public List<String> getErrors() {
        return errors;
    }

    @Override
    public EndpointResponse toEndpointResponse() {
        return new EndpointResponse(JsonRenderer.toJson(this).getBytes(StandardCharsets.UTF_8),
                EndpointResponse.contentTypeJson);
    }

    @Override
    public HtmlElement toHtml() {

        // Create the div and add the styles that will be used.
        HtmlElementList resultList = new HtmlElementList();
        resultList.add(new Style(".error { background-color: #f88; padding: 0.3rem; border: 1px solid #c00; " +
                "border-radius: 0.5rem; max-inline-size: fit-content; margin: 0.5rem 0 0.5rem 0; }" +
                ".notice { background-color: #ff8; padding: 0.3rem; border: 1px solid #cc0; border-radius: 0.5rem; " +
                "max-inline-size: fit-content; margin: 0.5rem 0 0.5rem 0; }" +
                ".table { display: table; border: 1px solid gray; border-radius: 0.5rem; margin: 0.5rem 0 0.5rem 0; " +
                "font-size: 0.8rem; }" +
                ".table div { padding: 0.1rem; }" +
                ".header-row > div { background-color: #ddd; }" +
                ".header-row > div:first-child { border-top-left-radius: 0.5rem; }" +
                ".header-row > div:last-child { border-top-right-radius: 0.5rem; }" +
                ".header-row { display: table-row; }" +
                ".header-row > div { display: table-cell; }" +
                ".header-row > div:not(:first-child) { border-left: 1px solid gray; }" +
                ".data-row { display: table-row; }" +
                ".data-row > div { display: table-cell; border-top: 1px solid gray; }" +
                ".data-row > div:not(:first-child) { border-left: 1px solid gray; }" +
                ".row-inverted { display: table-row; }" +
                ".row-inverted > div { display: table-cell; }" +
                ".row-inverted:not(:first-child) > div { border-top: 1px solid gray; }" +
                ".row-inverted > div:first-child { background-color: #ddd; }" +
                ".row-inverted:first-child > div:first-child { border-top-left-radius: 0.5rem; }" +
                ".row-inverted:last-child > div:first-child { border-bottom-left-radius: 0.5rem; }" +
                ".row-inverted > div:not(:first-child) { border-left: 1px solid gray; }" +
                ".extra-wrap { word-break: break-all; }"));

        // Add the errors.
        if (result != null) {
            for (String error : errors) {
                resultList.add(new P(error).attr("class", "error"));
            }
        }

        // Add the notices.
        if (notices != null) {
            for (String notice : notices) {
                resultList.add(new P(notice).attr("class", "notice"));
            }
        }

        // Add the result.
        for (CommandTable table : result) {
            if (table != null && table.getRows().size() > 0) {
                // Create the table.
                Div tableDiv = (Div) resultList.add(new Div().attr("class", "table"));

                if (table.isInvertedRowsColumns()) {
                    // This is the inverted rows/columns case. Render the header to the left.
                    int numberOfColumns = 1 + table.getRows().size();
                    for (int i = 0; i < table.getHeaders().length; i++) {
                        Div row = (Div) tableDiv.add(new Div().attr("class", "row-inverted"));
                        row.add(new Div().addRaw(table.getHeaders()[i].getLabel()));
                        for (Object[] dataRow : table.getRows()) {
                            String value = WebUtil.sanitizeString(dataRow[i] + "");
                            Div cell = (Div) row.add(new Div().addRaw(value));
                            if (table.getHeaders()[i].isExtraWrapColumn()) {
                                cell.attr("class", "extra-wrap");
                            }
                        }
                    }
                } else {
                    // This is the non-inverted case. Add the header row.
                    Div headerRowDiv = (Div) tableDiv.add(new Div().attr("class", "header-row"));
                    CommandTableHeader[] headers = table.getHeaders();
                    for (CommandTableHeader header : table.getHeaders()) {
                        headerRowDiv.add(new Div().addRaw(WebUtil.sanitizeString(header.getLabel())));
                    }

                    // Add the data rows.
                    for (Object[] row : table.getRows()) {
                        Div dataRowDiv = (Div) tableDiv.add(new Div().attr("class", "data-row"));
                        int numberOfColumns = Math.min(headers.length, row.length);
                        for (int i = 0; i < numberOfColumns; i++) {
                            String value = WebUtil.sanitizeString(row[i] + "");
                            Div cell = (Div) dataRowDiv.add(new Div().addRaw(value));
                            if (headers[i].isExtraWrapColumn()) {
                                cell.attr("class", "extra-wrap");
                            }
                        }
                    }
                }
            }
        }

        return resultList;
    }

    public void toConsole(CommandOutput output) {
        ConsoleUtil.printMessages(getNotices(), "NOTICE: ", ConsoleColor.Yellow, output);
        ConsoleUtil.printMessages(getErrors(), "ERROR: ", ConsoleColor.Red, output);
        for (CommandTable table : getResult()) {
            ConsoleUtil.printTable(table, output);
        }
    }
}
