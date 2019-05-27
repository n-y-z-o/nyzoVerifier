package co.nyzo.verifier;

import co.nyzo.verifier.util.LogUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.util.ArrayList;
import java.util.List;

public class MessageQueue {

    static {
        start();
    }

    private MessageCallback callback;
    private Message message;

    private MessageQueue(MessageCallback callback, Message message) {
        this.callback = callback;
        this.message = message;
    }

    private static final List<MessageQueue> queue = new ArrayList<>();
    private static boolean shouldPrintZeroOnRemoval = false;
    private static String lastMessageStatus = "";

    public static void blockThisThreadUntilClear() {

        boolean shouldPrint = true;
        int iteration = 0;
        while (queue.size() > 0) {
            if (shouldPrint) {
                shouldPrint = false;
                System.out.println("waiting for message queue to clear from thread [" +
                        Thread.currentThread().getName() + "], size is " + queue.size());
            }
            if (iteration++ % 20 == 18) {
                shouldPrint = true;
            }
            try {
                Thread.sleep(100L);
            } catch (Exception ignored) { }
        }

        // Sleep an additional 50ms to give the last message time to be processed.
        try {
            Thread.sleep(50L);
        } catch (Exception ignored) { }
    }

    public static synchronized void add(MessageCallback callback, Message message) {

        queue.add(new MessageQueue(callback, message));
        if (queue.size() % 100 == 0 && queue.size() > 0) {
            shouldPrintZeroOnRemoval = true;
            System.out.println("+ message queue is now " + queue.size() + ", " + (message == null ? "null" :
                    message.getType()));
        }
    }

    public static synchronized MessageQueue next() {

        MessageQueue queueObject = null;
        if (queue.size() > 0) {
            queueObject = queue.remove(0);
            if (queue.size() % 100 == 0 && (queue.size() > 0 || shouldPrintZeroOnRemoval)) {
                if (queue.size() == 0) {
                    shouldPrintZeroOnRemoval = false;
                }
                System.out.println("- message queue is now " + queue.size());
            }
        }

        return queueObject;
    }

    private static synchronized void start() {

        LogUtil.println("starting message queue");

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!UpdateUtil.shouldTerminate()) {

                    MessageQueue next = next();
                    String lastMessageStatus;
                    if (next == null) {
                        lastMessageStatus = "last message was null";
                        try {
                            Thread.sleep(100L);
                        } catch (Exception ignored) { }
                    } else {

                        lastMessageStatus = "last message was " + next.message;
                        try {
                            lastMessageStatus += " invoking responseReceived";
                            if (next.callback != null) {
                                lastMessageStatus += " [not null]";
                                next.callback.responseReceived(next.message);
                            }
                            lastMessageStatus += " [complete]";
                        } catch (Exception ignored) { }
                    }
                    MessageQueue.lastMessageStatus = lastMessageStatus;
                }
            }
        }, "MessageQueue-dispatchLoop").start();
    }
}
