package co.nyzo.verifier.scripts;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.debug.NewVerifierTallyStatusResponse;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class NewVerifierTallyStatusRequestScript {

    public static void main(String[] args) {

        if (args.length != 1) {
            System.out.println("\n\n\n*****************************************************************");
            System.out.println("required argument:");
            System.out.println("- private seed of your in-cycle verifier");
            System.out.println("*****************************************************************\n\n\n");
            return;
        }

        // Get the private seed and corresponding identifier that was provided as the argument.
        byte[] privateSeed = ByteUtil.byteArrayFromHexString(args[0], FieldByteSize.seed);
        byte[] inCycleVerifierIdentifier = KeyUtil.identifierForSeed(privateSeed);

        // Get the IP addresses of the verifier.
        List<byte[]> ipAddresses = ScriptUtil.ipAddressesForVerifier(inCycleVerifierIdentifier);
        if (ipAddresses.isEmpty()) {
            System.out.println("unable to find IP address of " +
                    ByteUtil.arrayAsStringWithDashes(inCycleVerifierIdentifier));
        }

        // Send the request to our verifier.
        AtomicInteger numberOfResponsesNotYetReceived = new AtomicInteger(ipAddresses.size());
        Message message = new Message(MessageType.NewVerifierTallyStatusRequest414, null);
        message.sign(privateSeed);
        for (byte[] ipAddress : ipAddresses) {
            Message.fetch(IpUtil.addressAsString(ipAddress), MeshListener.standardPort, message, new MessageCallback() {
                @Override
                public void responseReceived(Message message) {

                    if (message == null) {
                        System.out.println("response message is null");
                    } else {

                        // Get the response object from the message.
                        NewVerifierTallyStatusResponse response = (NewVerifierTallyStatusResponse) message.getContent();

                        // Sort the response descending on vote count.
                        List<String> lines = new ArrayList<>(response.getLines());
                        lines.sort(new Comparator<String>() {
                            @Override
                            public int compare(String line1, String line2) {

                                Integer value1 = Integer.parseInt(line1.split(":")[1].trim());
                                Integer value2 = Integer.parseInt(line2.split(":")[1].trim());

                                return value2.compareTo(value1);
                            }
                        });

                        // Print the response.
                        for (String line : lines) {
                            System.out.println(line);
                        }
                    }
                    numberOfResponsesNotYetReceived.decrementAndGet();
                }
            });
        }

        // Wait for the responses to return.
        while (numberOfResponsesNotYetReceived.get() > 0) {
            try {
                Thread.sleep(300L);
            } catch (Exception ignored) { }
        }

        // Terminate the application.
        UpdateUtil.terminate();
    }
}
