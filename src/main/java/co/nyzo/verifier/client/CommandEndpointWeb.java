package co.nyzo.verifier.client;

import co.nyzo.verifier.client.commands.Command;
import co.nyzo.verifier.web.*;
import co.nyzo.verifier.web.elements.*;

import java.util.*;

public class CommandEndpointWeb implements EndpointResponseProvider {

    private static final String actionKey = "action";
    private static final String actionValueBack = "back";
    private static final String actionValueReview = "review";
    private static final String actionValueRun = "run";

    private Command command;
    private HttpMethod method;

    public CommandEndpointWeb(Command command, HttpMethod method) {
        this.command = command;
        this.method = method;
    }

    @Override
    public EndpointResponse getResponse(EndpointRequest request) {

        EndpointResponse response;
        if (method == HttpMethod.Post || providesQueryParameterArgumentValues(request)) {
            response = processForm(request);
        } else {
            response = getFormPage(null, false);
        }

        return response;
    }

    private boolean providesQueryParameterArgumentValues(EndpointRequest request) {

        // Determine if the query parameters provide any of the argument values of the command.
        boolean providesQueryParameterArgumentValues = false;
        for (String argumentName : command.getArgumentIdentifiers()) {
            String argumentValue = request.getQueryParameters().get(argumentName);
            if (argumentValue != null && !argumentValue.trim().isEmpty()) {
                providesQueryParameterArgumentValues = true;
            }
        }

        return providesQueryParameterArgumentValues;
    }

    private EndpointResponse getFormPage(ValidationResult validationResult, boolean isConfirmation) {

        // Make the HTML page.
        Html html = (Html) new Html().attr("lang", "en");

        // Add the head and body to the page.
        String title = command == null ? "(null command)" : command.getDescription();
        Head head = (Head) html.add(new Head().addStandardMetadata(title));
        Body body = (Body) html.add(new Body().attr("style", "font-family: sans-serif;"));

        // Add the styles to the head.
        head.add(new Style(".content-container { max-width: 40rem; margin: auto; }" +
                ".form { border: 1px solid black; max-width: 40rem; padding: 1rem; border-radius: 0.5rem; }" +
                ".form-input-container { width: 100%; margin: 0.2rem; margin: 1.0rem 0 1.0rem 0; }" +
                ".form-label { width: 99%; }" +
                ".form-input { width: 99%; margin-top: 0.3rem; }" +
                ".form-input-disabled { border: none; padding: 3px; background-color: lightgray; }" +
                ".validation-error { color: red; font-style: italic; font-size: 0.7rem; border: 1px solid red; " +
                "border-radius: 0.5rem; padding: 0.2rem; white-space: nowrap; }" +
                ".validation-message { color: #080; font-style: italic; font-size: 0.7rem; border: 1px solid #080; " +
                "border-radius: 0.5rem; padding: 0.2rem; white-space: nowrap; }"));
        head.add(WebUtil.hoverButtonStyles);

        // Add a container for the page content.
        Div container = (Div) body.add(new Div().attr("class", "content-container"));

        // Add a button to return to the menu.
        container.add(new A().attr("href", "/").attr("class", "hover-button").addRaw("&larr;"));

        // Add the title.
        container.add(new H1(title));

        // Add the form.
        container.add(formElement(validationResult, isConfirmation));

        return new EndpointResponse(html.renderByteArray());
    }

    private Form formElement(ValidationResult validationResult, boolean isConfirmation) {

        Form form = (Form) new Form().attr("class", "form").attr("method", "post");
        String[] argumentNames = command.getArgumentNames();
        String[] argumentIdentifiers = command.getArgumentIdentifiers();
        for (int i = 0; i < argumentNames.length; i++) {
            // Add the container.
            Div argumentContainer = (Div) form.add(new Div().attr("class", "form-input-container"));

            // Get the validation message and argument value, if present.
            boolean argumentValueInvalid = false;
            String validationMessage = null;
            String argumentValue = "";
            if (validationResult != null) {
                ArgumentResult argumentResult = validationResult.getArgumentResults().get(i);
                argumentValueInvalid = !argumentResult.isValid();
                validationMessage = argumentResult.getValidationMessage();
                argumentValue = argumentResult.getValue();
            }

            // Add the label. If the argument was invalid, add a validation message.
            String argumentName = argumentNames[i];
            String argumentSuffix = validationMessage != null && !validationMessage.isEmpty() ? " <span class=\"" +
                    (argumentValueInvalid ? "validation-error" : "validation-message") + "\">" + validationMessage +
                    "</span>" : "";
            argumentContainer.add(new Label(argumentName + argumentSuffix).attr("class", "form-label"));

            // Add the input.
            Input input = (Input) argumentContainer.add(new Input()
                    .attr("class", "form-input" + (isConfirmation ? " form-input-disabled" : ""))
                    .attr("name", argumentIdentifiers[i])
                    .attr("value", argumentValue == null ? "" : argumentValue));
            if (isConfirmation) {
                input.attr("readonly", "readonly");
            }
        }

        // For confirmation pages, add a back button for editing.
        if (isConfirmation) {
            form.add(new Input().attr("type", "submit").attr("value", actionValueBack).attr("name", actionKey)
                    .attr("style", WebUtil.cancelButtonStyle + "margin-right: 0.3rem;"));
        }

        // For all pages, add a button to advance ("review" or "run command").
        String label = command.requiresConfirmation() && !isConfirmation ? actionValueReview : actionValueRun;
        form.add(new Input().attr("type", "submit").attr("value", label).attr("name", actionKey)
                .attr("style", WebUtil.acceptButtonStyle));

        return form;
    }

    private EndpointResponse processForm(EndpointRequest request) {

        // Get the argument values in an ordered list.
        List<String> argumentValues = new ArrayList<>();
        Map<String, String> parameters = method == HttpMethod.Post ? request.getPostParameters() :
                request.getQueryParameters();
        for (String argumentIdentifier : command.getArgumentIdentifiers()) {
            argumentValues.add(parameters.getOrDefault(argumentIdentifier, "").trim());
        }

        // If the command requires validation, validate it now. Otherwise, create an auto-approve validation.
        EndpointResponse response = null;
        ValidationResult validationResult;
        if (command.requiresValidation()) {
            CommandOutput output = new CommandOutputWeb();
            validationResult = command.validate(argumentValues, output);
        } else {
            List<ArgumentResult> argumentResults = new ArrayList<>();
            for (String argumentValue : argumentValues) {
                argumentResults.add(new ArgumentResult(true, argumentValue));
            }
            validationResult = new ValidationResult(argumentResults);
        }

        // If the action is "back" or "review", or if the validation failed, return to the form. Otherwise, run the
        // command.
        String action = parameters.getOrDefault("action", "back");
        if (action.equals(actionValueBack) || action.equals(actionValueReview) ||
                validationResult.numberOfInvalidArguments() > 0) {
            boolean isConfirmation = action.equals(actionValueReview) &&
                    validationResult.numberOfInvalidArguments() == 0;
            response = getFormPage(validationResult, isConfirmation);
        } else {
            response = getProgressPage(argumentValues);
        }

        return response;
    }

    private EndpointResponse getProgressPage(List<String> argumentValues) {

        // Make the HTML page.
        Html html = (Html) new Html().attr("lang", "en");

        // Add the head and body to the page.
        String title = (command == null ? "(null command)" : command.getDescription());
        Head head = (Head) html.add(new Head().addStandardMetadata(title));
        Body body = (Body) html.add(new Body().attr("style", "font-family: sans-serif;"));

        // Add the styles to the head.
        head.add(new Style("h1 { position: absolute; top: 1.0rem; font-size: 1.3rem; margin: 0; white-space: nowrap; " +
                "overflow: hidden; text-overflow: ellipsis; text-align: center; left: 3rem; right: 3rem; }" +
                ".progress-box { background-color: lightgray; position: absolute; top: 3.5rem; bottom: 1%; " +
                "left: 1%; right: 1%; font-family: monospace; white-space: pre; font-size: 0.7rem; " +
                "overflow: scroll; }" +
                ".command-complete { font-weight: bold; }"));
        head.add(WebUtil.hoverButtonStyles);

        // Add a button to return to the menu.
        body.add(new A().attr("href", "/").attr("class", "hover-button").addRaw("&larr;"));

        // Add the title.
        body.add(new H1(title));

        if (command.isLongRunning()) {
            // Make the command-output handler and make the progress box.
            CommandOutputWeb commandOutput = new CommandOutputWeb();
            CommandOutputWebManager.register(commandOutput);
            Div progress = (Div) body.add(new Div().attr("class", "progress-box").attr("id", "progress-box"));
            body.add(progressUpdateScript(progress.getAttr("id"), commandOutput.getIdentifier()));

            // Run the command asynchronously.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    ExecutionResult result = command.run(argumentValues, commandOutput);
                    if (result != null) {
                        result.toConsole(commandOutput);
                    }
                    commandOutput.setComplete();
                }
            }).start();
        } else {
            // For commands that complete immediately, run the command synchronously and render the results.
            CommandOutputWeb commandOutput = new CommandOutputWeb();
            ExecutionResult result = null;
            try {
                result = command.run(argumentValues, commandOutput);
            } catch (Exception ignored) { }

            // If the result is null, create an error result.
            if (result == null) {
                result = new SimpleExecutionResult(null, null,
                        Collections.singletonList("The command did not produce a result."));
            }

            // Render the result.
            body.add(result.toHtml());
        }

        return new EndpointResponse(html.renderByteArray());
    }

    private static Script progressUpdateScript(String elementIdentifier, String outputIdentifier) {

        StringBuilder script = new StringBuilder();

        // Wrap the script in a function for local variable scoping.
        script.append("(function() {");

        // Store the current timestamp and the arguments. The timestamp is used to ensure the div does not contain
        // out-of-date content.
        long interval = 1000L;
        String endpoint = ClientController.commandOutputEndpoint + "?id=" + outputIdentifier;
        script.append("var interval = ").append(interval).append(";");
        script.append("var id = '").append(elementIdentifier).append("';");
        script.append("var endpoint = '").append(endpoint).append("';");

        // This is a simple Ajax update performed at the specified interval (ms). The indentation is for readability in
        // this code, not formatting in the rendered page.
        script.append("var refreshFunction = function() {");
        script.append("  console.log('refresh');");
        script.append("  var refreshRequest = new XMLHttpRequest();");
        script.append("  refreshRequest.onreadystatechange = function() {");
        script.append("    if (this.readyState == 4 && this.status == 200) {");
        script.append("      var openSpan = '';");
        script.append("      var closeSpan = '';");
        script.append("      if (this.responseText === '" + ClientController.commandCompleteString + "') {");
        script.append("        clearInterval(intervalIdentifier);");
        script.append("        openSpan = '<span class=\"command-complete\"><br>';");
        script.append("        closeSpan = '</span>';");
        script.append("      }");
        script.append("      var element = document.getElementById(id);");
        script.append("      var separator = element.innerHTML.length == 0 || this.responseText.length == 0 ?");
        script.append("                          '' : '<br>';");
        script.append("      element.innerHTML = element.innerHTML + separator + openSpan + this.responseText");
        script.append("                              + closeSpan;");
        script.append("    }");
        script.append("  };");
        script.append("  refreshRequest.open('GET', endpoint, true);");
        script.append("  refreshRequest.send();");
        script.append("};");

        script.append("setTimeout(refreshFunction, 400);");
        script.append("var intervalIdentifier = setInterval(refreshFunction, ").append(interval).append(");");

        // Close the function that wraps the script.
        script.append("})();");

        return new Script(script.toString());
    }

    @Override
    public String toString() {
        return "[CommandEndpointWeb:" + (command == null ? "(null)" : command.getLongCommand()) + "]";
    }
}
