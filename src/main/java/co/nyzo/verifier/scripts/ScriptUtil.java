package co.nyzo.verifier.scripts;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.MeshResponse;
import co.nyzo.verifier.util.IpUtil;

import java.util.concurrent.atomic.AtomicBoolean;

public class ScriptUtil {

    public static byte[] ipAddressForVerifier(byte[] identifier) {

        // Ask Nyzo verifier 0 for the mesh. Get the IP address of the verifier.
        byte[] ipAddress = new byte[FieldByteSize.ipAddress];
        Message meshRequest = new Message(MessageType.MeshRequest15, null);
        AtomicBoolean receivedResponse = new AtomicBoolean(false);
        Message.fetch("verifier0.nyzo.co", MeshListener.standardPort, meshRequest, new MessageCallback() {

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
                Thread.sleep(300L);
            } catch (Exception ignored) { }
        }
        System.out.println("found IP address: " + IpUtil.addressAsString(ipAddress));

        return ipAddress;
    }
}
