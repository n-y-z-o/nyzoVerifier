package co.nyzo.verifier;

import co.nyzo.verifier.util.UpdateUtil;

import java.util.ArrayList;
import java.util.List;

public class MessageQueue {

    private MessageCallback callback;
    private Message message;

    private MessageQueue(MessageCallback callback, Message message) {
        this.callback = callback;
        this.message = message;
    }

    static {
        start();
    }

    private static final List<MessageQueue> queue = new ArrayList<>();

    public static synchronized void add(MessageCallback callback, Message message) {

        queue.add(new MessageQueue(callback, message));
    }

    public static synchronized MessageQueue next() {

        MessageQueue queueObject = null;
        if (queue.size() > 0) {
            queueObject = queue.remove(0);
        }

        return queueObject;
    }

    private static void start() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!UpdateUtil.shouldTerminate()) {

                    MessageQueue next = next();
                    if (next == null) {
                        try {
                            Thread.sleep(100L);
                        } catch (Exception ignored) { }
                    } else {
                        try {
                            next.callback.responseReceived(next.message);
                        } catch (Exception ignored) {
                            System.err.println("exception processing message " + next.message + " (" +
                                    ignored.getMessage() + ")");
                        }
                    }

                }
            }
        }).start();
    }
}
