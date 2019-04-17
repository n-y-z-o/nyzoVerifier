package co.nyzo.verifier.recovery;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.HashVoteOverrideRequest;
import co.nyzo.verifier.messages.MeshResponse;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class UnfreezeMe {

    public static void main(String[] args) {

        if (args.length == 0) {
            System.out.println("please provide a private seed as the first argument");
        }

        // Declare private seed and derive identifier from it.
        byte[] privateSeed = ByteUtil.byteArrayFromHexString(args[0], FieldByteSize.seed);
        byte[] identifier = KeyUtil.identifierForSeed(privateSeed);

        // Ask Nyzo verifier 0 for the mesh. Get the IP address of our verifier.
        byte[] ipAddress = new byte[FieldByteSize.ipAddress];
        Message meshRequest = new Message(MessageType.MeshRequest15, null);
        AtomicBoolean receivedResponse = new AtomicBoolean(false);
        Message.fetchTcp("verifier0.nyzo.co", MeshListener.standardPortTcp, meshRequest, new MessageCallback() {

            @Override
            public void responseReceived(Message message) {

                MeshResponse meshResponse = (MeshResponse) message.getContent();
                for (Node node : meshResponse.getMesh()) {
                    if (ByteUtil.arraysAreEqual(identifier, node.getIdentifier())) {
                        for (int i = 0; i < FieldByteSize.ipAddress; i++) {
                            ipAddress[i] = node.getIpAddress()[i];
                        }
                        receivedResponse.set(true);
                    }
                }
            }
        });

        // Wait for the response to return.
        while (!receivedResponse.get()) {
            try {
                Thread.sleep(1000L);
            } catch (Exception ignored) { }
        }
        System.out.println("found IP address: " + IpUtil.addressAsString(ipAddress));

        // Declare the hash and height for which we want to vote.
        long height = 529916L;
        byte[] hash = ByteUtil.byteArrayFromHexString("52e273fcea2f0452-e72e80c642dafdaf-c339f1b8dbe6b032-" +
                "9fe4be70e6a85c8a", FieldByteSize.hash);

        // Send the override to our verifier.
        receivedResponse.set(false);
        Message message = new Message(MessageType.HashVoteOverrideRequest29, new HashVoteOverrideRequest(height, hash));
        message.sign(privateSeed);
        Message.fetchTcp(IpUtil.addressAsString(ipAddress), MeshListener.standardPortTcp, message,
                new MessageCallback() {
                    @Override
                    public void responseReceived(Message message) {
                        System.out.println("response is " + message);
                        receivedResponse.set(true);
                    }
                });

        // Wait for the response to return.
        while (!receivedResponse.get()) {
            try {
                Thread.sleep(1000L);
            } catch (Exception ignored) { }
        }
        System.out.println("received override response");

        UpdateUtil.terminate();
    }
}
