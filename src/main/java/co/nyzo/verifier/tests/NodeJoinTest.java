package co.nyzo.verifier.tests;

import co.nyzo.verifier.MeshListener;
import co.nyzo.verifier.Message;
import co.nyzo.verifier.MessageCallback;
import co.nyzo.verifier.MessageType;
import co.nyzo.verifier.messages.NodeJoinMessage;
import co.nyzo.verifier.messages.NodeJoinResponse;
import co.nyzo.verifier.util.UpdateUtil;

import java.util.concurrent.atomic.AtomicInteger;

public class NodeJoinTest {

    // This test sends a node-join message to check the response. It keeps sending until it receives a response that
    // contains at least one vote.
    private static final String node = "verifier0.nyzo.co";

    public static void main(String[] args) {

        AtomicInteger totalVotes = new AtomicInteger(0);
        while (totalVotes.get() == 0) {
            Message message = new Message(MessageType.NodeJoin3, new NodeJoinMessage());
            Message.fetch(node, MeshListener.standardPort, message, new MessageCallback() {
                @Override
                public void responseReceived(Message message) {

                    NodeJoinResponse response = (NodeJoinResponse) message.getContent();
                    System.out.println("nickname is " + response.getNickname());
                    System.out.println("response has " + response.getBlockVotes().size() + " block votes");
                    System.out.println("response new-verifier vote: " + response.getNewVerifierVote());
                    totalVotes.addAndGet(response.getBlockVotes().size());
                }
            });

            try {
                Thread.sleep(500L);
            } catch (Exception ignored) { }
        }

        UpdateUtil.terminate();
        MeshListener.closeSocket();
    }
}
