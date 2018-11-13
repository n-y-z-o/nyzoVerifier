package co.nyzo.verifier;

import co.nyzo.verifier.util.DebugUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private static boolean inBadState = false;
    private static String lastMessageStatus = "";

    public static void blockThisThreadUntilClear() {

        boolean shouldPrint = true;
        int iteration = 0;
        while (queue.size() > 0) {
            Set<MessageType> messageTypes = new HashSet<>();
            synchronized (MessageQueue.class) {
                for (MessageQueue item : queue) {
                    if (item.message != null) {
                        messageTypes.add(item.message.getType());
                    }
                }
            }
            if (shouldPrint) {
                shouldPrint = false;
                System.out.println("waiting for message queue to clear from thread [" +
                        Thread.currentThread().getName() + "], (" + messageTypes + "), size is " + queue.size() +
                        ", in bad state: " + inBadState + (inBadState ? " [" + lastMessageStatus + "]" : ""));
            }
            if (iteration++ % 20 == 18) {
                shouldPrint = true;
            }
            if (iteration > 40) {
                inBadState = true;
            }
            try {
                Thread.sleep(500L);
            } catch (Exception ignored) { }
        }
    }

    public static synchronized void add(MessageCallback callback, Message message) {

        if (inBadState) {
            System.out.println("*** MessageQueue in bad state *** -- adding to queue");
        }

        queue.add(new MessageQueue(callback, message));
        if (queue.size() % 100 == 0 && queue.size() > 0) {
            shouldPrintZeroOnRemoval = true;
            System.out.println("+ message queue is now " + queue.size() + ", " + (message == null ? "null" :
                    message.getType()));
        }
    }

    public static synchronized MessageQueue next() {

        if (inBadState) {
            System.out.println("*** MessageQueue in bad state *** -- fetching next from queue, size of queue is " +
                    queue.size());
        }

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

        System.out.println("starting message queue");

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!UpdateUtil.shouldTerminate()) {

                    if (inBadState) {
                        System.out.println("getting next");
                    }
                    MessageQueue next = next();
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
                        } catch (Exception ignored) {
                            System.err.println("exception processing message " + next.message + " (" +
                                    ignored.getMessage() + "): " + DebugUtil.callingMethods(8));
                        }
                    }

                }
            }
        }, "MessageQueue-dispatchLoop").start();
    }
}
