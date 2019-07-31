package co.nyzo.verifier;

import java.net.Socket;

public class Connection {

    private long timestamp;
    private Socket socket;

    public Connection(Socket socket) {
        this.timestamp = System.currentTimeMillis();
        this.socket = socket;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Socket getSocket() {
        return socket;
    }
}
