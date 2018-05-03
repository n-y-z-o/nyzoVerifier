package co.nyzo.verifier.tests;

import co.nyzo.verifier.MeshListener;
import co.nyzo.verifier.Message;
import co.nyzo.verifier.MessageCallback;
import co.nyzo.verifier.MessageType;
import co.nyzo.verifier.messages.BlockRequest;
import co.nyzo.verifier.util.UpdateUtil;

import java.util.concurrent.atomic.AtomicBoolean;

public class BlockRequestTest {

    public static void main(String[] args) {

        MeshListener.start();

        AtomicBoolean receivedResponse = new AtomicBoolean(false);
        Message message = new Message(MessageType.BlockRequest11, new BlockRequest(0, true));
        System.out.println("message byte length: " + message.getBytesForTransmission().length);
        Message.fetch("localhost", 9444, message, false, new MessageCallback() {
            @Override
            public void responseReceived(Message message) {
                System.out.println("received response");
                receivedResponse.set(true);
            }
        });

        while (!receivedResponse.get()) {
            try { Thread.sleep(1000); }
            catch (Exception ignored) { }
        }

        MeshListener.closeServerSocket();
        UpdateUtil.terminate();
    }
}
