package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.*;
import co.nyzo.verifier.messages.StatusResponse;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.ThreadUtil;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class VerifierStatusCommand implements Command {

    @Override
    public String getShortCommand() {
        return "VS";
    }

    @Override
    public String getLongCommand() {
        return "verifierStatus";
    }

    @Override
    public String getDescription() {
        return "check the status of a verifier";
    }

    @Override
    public String[] getArgumentNames() {
        return new String[] { "IP address", "port (optional, default=9444)" };
    }

    @Override
    public String[] getArgumentIdentifiers() {
        return new String[] { "ipAddress", "port" };
    }

    @Override
    public boolean requiresValidation() {
        return true;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public boolean isLongRunning() {
        return true;
    }

    @Override
    public ValidationResult validate(List<String> argumentValues, CommandOutput output) {

        ValidationResult result = null;
        try {
            // Make a list for the argument result items.
            List<ArgumentResult> argumentResults = new ArrayList<>();

            // Check the IP address.
            String ipAddressString = argumentValues.get(0).trim();
            if (IpUtil.isValidAddress(ipAddressString)) {
                argumentResults.add(new ArgumentResult(true,
                        IpUtil.addressAsString(IpUtil.addressFromString(ipAddressString))));
            } else {
                String message = ipAddressString.isEmpty() ? "missing IP address" : "not a valid IP address";
                argumentResults.add(new ArgumentResult(false, ipAddressString, message));
            }

            // The port is optional. If no value or an invalid value is provided, the default of 9444 will be used.
            argumentResults.add(new ArgumentResult(true, argumentValues.get(1).trim()));

            // Produce the result.
            result = new ValidationResult(argumentResults);

        } catch (Exception ignored) { }

        // If the confirmation result is null, create an exception result. This will only happen if an exception is not
        // handled properly by the validation code.
        if (result == null) {
            result = ValidationResult.exceptionResult(getArgumentNames().length);
        }

        return result;
    }

    @Override
    public ExecutionResult run(List<String> argumentValues, CommandOutput output) {

        try {
            // Get the IP address.
            byte[] ipAddress = IpUtil.addressFromString(argumentValues.get(0).trim());

            // The port is optional. If no value or an invalid value is provided, the default of 9444 will be used.
            int port;
            try {
                port = Integer.parseInt(argumentValues.get(1).trim());
                if (port < 0 || port > 65535) {
                    port = MeshListener.standardPortTcp;
                }
            } catch (Exception e) {
                port = MeshListener.standardPortTcp;
            }
            int portFinal = port;

            // Check the verifier.
            Message message = new Message(MessageType.StatusRequest17, null);
            AtomicBoolean receivedResponse = new AtomicBoolean(false);
            Message.fetchTcp(IpUtil.addressAsString(ipAddress), port, message, new MessageCallback() {
                @Override
                public void responseReceived(Message message) {

                    if (message == null) {
                        output.println("did not receive a response from " + IpUtil.addressAsString(ipAddress) + ":" +
                                portFinal);
                    } else if (!(message.getContent() instanceof StatusResponse)) {
                        output.println("response message content from " + IpUtil.addressAsString(ipAddress) + ":" +
                                portFinal + " is invalid");
                    } else {
                        // Get the response object from the message.
                        StatusResponse response = (StatusResponse) message.getContent();

                        // Print the response.
                        for (String line : response.getLines()) {
                            output.println(line);
                        }
                    }

                    receivedResponse.set(true);
                }
            });

            while (!receivedResponse.get()) {
                ThreadUtil.sleep(200);
            }

        } catch (Exception e) {
            output.println(ConsoleColor.Red + "unexpected issue checking verifier: " + PrintUtil.printException(e) +
                    ConsoleColor.reset);
        }

        // ExecutionResult objects are not yet implemented for long-running commands.
        return null;
    }
}
