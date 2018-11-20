package co.nyzo.verifier.scripts;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.HashVoteOverrideRequest;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.util.concurrent.atomic.AtomicBoolean;

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

        // Get the IP address of the verifier.
        byte[] ipAddress = ScriptUtil.ipAddressForVerifier(inCycleVerifierIdentifier);
        if (ByteUtil.isAllZeros(ipAddress)) {
            System.out.println("unable to find IP address of " +
                    ByteUtil.arrayAsStringWithDashes(inCycleVerifierIdentifier));
            return;
        }

        // Get the height and hash from the arguments.
        long height = 0L;
        try {
            height = Long.parseLong(args[1]);
        } catch (Exception ignored) { }

        byte[] hash = ByteUtil.byteArrayFromHexString(args[2], FieldByteSize.hash);

        // Send the request to our verifier.
        AtomicBoolean receivedResponse = new AtomicBoolean(false);
        HashVoteOverrideRequest request = new HashVoteOverrideRequest(height, hash);
        Message message = new Message(MessageType.HashVoteOverrideRequest29, request);
        message.sign(privateSeed);
        Message.fetch(IpUtil.addressAsString(ipAddress), MeshListener.standardPort, message, new MessageCallback() {
            @Override
            public void responseReceived(Message message) {

                System.out.println("response is " + message);
                receivedResponse.set(true);
            }
        });

        // Wait for the response to return.
        while (!receivedResponse.get()) {
            try {
                Thread.sleep(300L);
            } catch (Exception ignored) { }
        }

        // Terminate the application.
        UpdateUtil.terminate();
    }
}
