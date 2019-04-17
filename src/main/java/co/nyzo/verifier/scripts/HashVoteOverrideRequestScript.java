package co.nyzo.verifier.scripts;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.HashVoteOverrideRequest;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class HashVoteOverrideRequestScript {

    public static void main(String[] args) {

        if (args.length != 3) {
            System.out.println("\n\n\n*****************************************************************");
            System.out.println("required arguments:");
            System.out.println("- private seed of your in-cycle verifier");
            System.out.println("- height of the override block vote");
            System.out.println("- hash of the override block vote");
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

        // Get the height and hash from the arguments.
        long height = 0L;
        try {
            height = Long.parseLong(args[1]);
        } catch (Exception ignored) { }

        byte[] hash = ByteUtil.byteArrayFromHexString(args[2], FieldByteSize.hash);

        // Send the request to our verifier instances.
        AtomicInteger numberOfResponsesNotYetReceived = new AtomicInteger(ipAddresses.size());
        HashVoteOverrideRequest request = new HashVoteOverrideRequest(height, hash);
        Message message = new Message(MessageType.HashVoteOverrideRequest29, request);
        message.sign(privateSeed);
        for (byte[] ipAddress : ipAddresses) {
            Message.fetchTcp(IpUtil.addressAsString(ipAddress), MeshListener.standardPortTcp, message,
                    new MessageCallback() {
                        @Override
                        public void responseReceived(Message message) {

                            System.out.println("response is " + message);
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
