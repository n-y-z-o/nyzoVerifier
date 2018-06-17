package co.nyzo.verifier.tests;

import co.nyzo.verifier.MeshListener;
import co.nyzo.verifier.Message;
import co.nyzo.verifier.MessageCallback;
import co.nyzo.verifier.MessageType;
import co.nyzo.verifier.messages.NodeJoinMessage;
import co.nyzo.verifier.messages.NodeJoinResponse;

public class NodeListMessageTest {

    public static void main(String[] args) {

        System.out.println("sending initial fetch");
        String url = "verifier1.nyzo.co";

        Message.fetch(url, MeshListener.standardPort, new Message(MessageType.NodeJoin3, new NodeJoinMessage(9444)),
                new MessageCallback() {
                    @Override
                    public void responseReceived(Message message) {
                        System.out.println("return message in NodeJoin3 is " + message);
                        NodeJoinResponse response = (NodeJoinResponse) message.getContent();
                        if (response != null) {
                            System.out.println("nickname is " + response.getNickname());
                        }
                    }
                });

        try {
            Thread.sleep(1000L);
        } catch (Exception ignored) { }

        Message.fetch(url, MeshListener.standardPort, new Message(MessageType.BootstrapRequest1, null),
                new MessageCallback() {
                    @Override
                    public void responseReceived(Message message) {
                        System.out.println("return message in BootstrapRequest1 is " + message);
                    }
                });
    }
}
