package co.nyzo.verifier.tests;

import co.nyzo.verifier.Message;
import co.nyzo.verifier.MessageCallback;
import co.nyzo.verifier.MessageType;
import co.nyzo.verifier.NodeManager;
import co.nyzo.verifier.util.UpdateUtil;

public class PingBroadcastTest {

    public static void main(String[] args) throws Exception {

        Message pingMessage = new Message(MessageType.Ping200, null);
        for (int i = 0; i < 4; i++) {
            String node = "verifier" + i + ".nyzo.co";
            Message.fetch(node, 9444, pingMessage, false, new MessageCallback() {

                @Override
                public void responseReceived(Message message) {
                    System.out.println("received response from " + node + ": " + message);
                }
            });
        }

        Thread.sleep(2000L);

        Message statusMessage = new Message(MessageType.StatusRequest17, null);
        for (int i = 0; i < 4; i++) {
            String node = "verifier" + i + ".nyzo.co";
            Message.fetch(node, 9444, statusMessage, false, new MessageCallback() {

                @Override
                public void responseReceived(Message message) {
                    System.out.println("received response from " + node + ": " + message);
                }
            });
        }

        Thread.sleep(2000L);
        UpdateUtil.terminate();
    }
}
