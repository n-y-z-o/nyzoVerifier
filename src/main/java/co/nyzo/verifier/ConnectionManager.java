package co.nyzo.verifier;

import co.nyzo.verifier.util.PreferencesUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.ThreadUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ConnectionManager {

    private static final long closeDelay = PreferencesUtil.getLong("connection_close_delay", 500L);
    private static final ConcurrentLinkedQueue<Connection> connections = new ConcurrentLinkedQueue<>();

    static {
        start();
    }

    private static void start() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                int i = 0;
                while (!UpdateUtil.shouldTerminate()) {
                    try {
                        // The queue is FIFO. If there is no connection in the queue, sleeping for the close delay is
                        // safe, because any connection added after that will need at least that delay.
                        Connection connection = connections.poll();
                        if (connection == null) {
                            ThreadUtil.sleep(closeDelay);
                        } else {
                            long sleepTime = closeDelay + connection.getTimestamp() - System.currentTimeMillis();
                            if (sleepTime > 0) {
                                ThreadUtil.sleep(sleepTime);
                            }

                            fastCloseSocket(connection.getSocket());
                        }
                    } catch (Exception e) {
                        System.out.println("exception in ConnectionManager loop: " + PrintUtil.printException(e));
                    }
                }
            }
        }).start();
    }

    public static void slowCloseSocket(Socket socket) {

        if (socket != null) {
            // Attempt to add the connection to the queue. If unsuccessful, close the socket immediately.
            Connection connection = new Connection(socket);
            if (!connections.offer(connection)) {
                fastCloseSocket(socket);
            }
        }
    }

    public static void fastCloseSocket(Socket socket) {

        if (socket != null) {
            try {
                socket.setSoLinger(true, 0);
                socket.close();
            } catch (Exception ignored) { }
        }
    }
}
