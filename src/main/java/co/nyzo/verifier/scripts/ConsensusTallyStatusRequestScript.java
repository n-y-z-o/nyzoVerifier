package co.nyzo.verifier.scripts;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.debug.ConsensusTallyStatusResponse;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ConsensusTallyStatusRequestScript {

    public static void main(String[] args) {

        // Get the private seed. If none is provided, we will send a request to the loopback address.
        byte[] privateSeed;
        if (args.length < 1) {
            System.out.println("*** no in-cycle verifier seed provided; using local verifier seed ***");
            privateSeed = null;
        } else {
            privateSeed = ByteUtil.byteArrayFromHexString(args[0], FieldByteSize.seed);
        }

        // Get the corresponding identifier.
        byte[] inCycleVerifierIdentifier = privateSeed == null ? null : KeyUtil.identifierForSeed(privateSeed);

        // Get the IP addresses of the verifier.
        List<byte[]> ipAddresses = inCycleVerifierIdentifier == null ?
                Collections.singletonList(IpUtil.addressFromString("127.0.0.1")) :
                ScriptUtil.ipAddressesForVerifier(inCycleVerifierIdentifier);
        if (ipAddresses.isEmpty()) {
            System.out.println("unable to find IP address of " +
                    ByteUtil.arrayAsStringWithDashes(inCycleVerifierIdentifier));
        }

        // Send the request to our verifier instances.
        AtomicInteger numberOfResponsesNotYetReceived = new AtomicInteger(ipAddresses.size());
        Message message = new Message(MessageType.ConsensusTallyStatusRequest412, null);
        if (privateSeed != null) {
            message.sign(privateSeed);
        }
        for (byte[] ipAddress : ipAddresses) {
            Message.fetch(IpUtil.addressAsString(ipAddress), MeshListener.standardPort, message, new MessageCallback() {
                @Override
                public void responseReceived(Message message) {

                    System.out.println("response message: " + message);
                    if (message != null) {
                        if (message.getContent() instanceof ConsensusTallyStatusResponse) {
                            ConsensusTallyStatusResponse response = (ConsensusTallyStatusResponse) message.getContent();
                            System.out.println("response number of lines: " + response.getLines().size());
                            for (String line : response.getLines()) {
                                System.out.println(line);
                            }
                        } else {
                            System.out.println("content is incorrect type: " + message.getContent());
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
