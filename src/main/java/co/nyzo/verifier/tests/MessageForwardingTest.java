package co.nyzo.verifier.tests;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.UpdateUtil;

import java.util.Map;

public class MessageForwardingTest {

    public static void main(String[] args) throws Exception {

        printThreadInfo();

        MeshListener.start();
        Block block = BlockManager.frozenBlockForHeight(0L);
        Message.fetch("127.0.0.1", 9444, new Message(MessageType.NewBlock9, block), false, new MessageCallback() {
            @Override
            public void responseReceived(Message message) {
                System.out.println("received response: " + message);
            }
        });

        Thread.sleep(1000L);

        UpdateUtil.terminate();
        MeshListener.closeSocket();
    }

    private static void printThreadInfo() {

        System.out.println("==================");
        System.out.println("active threads: " + Thread.activeCount());

        Map<Thread, StackTraceElement[]> stackTraces = Thread.getAllStackTraces();
        for (Thread thread : stackTraces.keySet()) {
            StackTraceElement[] elements = stackTraces.get(thread);
            System.out.println(thread.getName() + ", " + thread.getState() + ": " + elements.length);
        }

        System.out.println();
    }
}
