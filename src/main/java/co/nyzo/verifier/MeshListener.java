package co.nyzo.verifier;

import co.nyzo.verifier.messages.*;
import co.nyzo.verifier.util.FileUtil;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class MeshListener {

    public static void main(String[] args) {
        start();
    }

    private static final AtomicBoolean alive = new AtomicBoolean(false);

    public static boolean isAlive() {
        return alive.get();
    }

    public static final int statusPort = 9443;
    public static final int standardPort = 9444;

    private static ServerSocket serverSocket = null;
    private static ServerSocket statusSocket = null;
    private static int port;

    private static byte[] statusBytes =
            ("HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/plain; charset=utf-8\r\n" +
                    "Cache-Control: no-cache\r\n" +
                    "Connection: Close\r\n" +
                    "Content-Length: 28\r\n" +
                    "\r\n" +
                    "Hello from the status port!\r\n").getBytes(StandardCharsets.UTF_8);

    public static int getPort() {
        return port;
    }

    public static void start() {

        if (!alive.getAndSet(true)) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        statusSocket = new ServerSocket(statusPort);

                        while (!UpdateUtil.shouldTerminate()) {

                            try {
                                Socket clientSocket = statusSocket.accept();
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {

                                        try {
                                            clientSocket.getOutputStream().write(statusBytes);
                                        } catch (Exception ignored) { }

                                        try {
                                            Thread.sleep(3L);
                                            clientSocket.close();
                                        } catch (Exception ignored) { }
                                    }
                                }, "MeshListener-statusClientSocket").start();
                            } catch (Exception ignored) {
                            }
                        }

                        closeSockets();
                    } catch (Exception ignored) { }
                }
            }, "MeshListener-statusSocket").start();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        serverSocket = new ServerSocket(standardPort);
                        port = serverSocket.getLocalPort();

                        while (!UpdateUtil.shouldTerminate()) {

                            try {
                                Socket clientSocket = serverSocket.accept();
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {

                                        try {
                                            Message message = Message.readFromStream(clientSocket.getInputStream(),
                                                    IpUtil.addressFromString(clientSocket.getRemoteSocketAddress() + ""),
                                                    MessageType.IncomingRequest65533);
                                            if (message != null) {
                                                Message response = response(message);
                                                if (response != null) {
                                                    clientSocket.getOutputStream().write(response
                                                            .getBytesForTransmission());
                                                }
                                            }

                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }

                                        try {
                                            Thread.sleep(3L);
                                            clientSocket.close();
                                        } catch (Exception ignored) { }
                                    }
                                }, "MeshListener-clientSocket").start();
                            } catch (Exception ignored) { }
                        }

                        closeSockets();

                    } catch (Exception ignored) { }

                    alive.set(false);
                }
            }, "MeshListener-serverSocket").start();
        }
    }

    public static void closeSockets() {

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception ignored) {
            }
            serverSocket = null;
        }

        if (statusSocket != null) {
            try {
                statusSocket.close();
            } catch (Exception ignored) {
            }
            statusSocket = null;
        }
    }

    public static Message response(Message message) {

        // This is the single point of dispatch for responding to all received messages.

        Message response = null;
        try {
            if (message != null && message.isValid()) {

                Verifier.registerMessage();

                MessageType messageType = message.getType();

                if (messageType == MessageType.BootstrapRequest1) {

                    // Update the node with the node manager so it will appear in the node list that it receives.
                    BootstrapRequest requestMessage = (BootstrapRequest) message.getContent();
                    NodeManager.updateNode(message.getSourceNodeIdentifier(), message.getSourceIpAddress(),
                            requestMessage.getPort());

                    response = new Message(MessageType.BootstrapResponse2, new BootstrapResponse());

                } else if (messageType == MessageType.NodeJoin3) {

                    System.out.println("received node-join message");
                    NodeJoinMessage nodeJoinMessage = (NodeJoinMessage) message.getContent();
                    NodeManager.updateNode(message.getSourceNodeIdentifier(), message.getSourceIpAddress(),
                            nodeJoinMessage.getPort());

                    response = new Message(MessageType.NodeJoinResponse4, null);

                } else if (messageType == MessageType.Transaction5) {

                    response = new Message(MessageType.TransactionResponse6,
                            new TransactionResponse((Transaction) message.getContent()));

                    Message.forward(message);

                } else if (messageType == MessageType.PreviousHashRequest7) {

                    response = new Message(MessageType.PreviousHashResponse8, new PreviousHashResponse());

                } else if (messageType == MessageType.NewBlock9) {

                    boolean shouldForwardBlock = ChainOptionManager.registerBlock((Block) message.getContent());

                    response = new Message(MessageType.NewBlockResponse10, null);
                    if (shouldForwardBlock) {
                        Message.forward(message);
                    }

                } else if (messageType == MessageType.BlockRequest11) {

                    BlockRequest request = (BlockRequest) message.getContent();
                    response = new Message(MessageType.BlockResponse12, new BlockResponse(request.getStartHeight(),
                            request.getEndHeight(), request.includeBalanceList()));

                } else if (messageType == MessageType.TransactionPoolRequest13) {

                    response = new Message(MessageType.TransactionPoolResponse14,
                            new TransactionPoolResponse(TransactionPool.allTransactions()));

                } else if (messageType == MessageType.MeshRequest15) {

                    response = new Message(MessageType.MeshResponse16, new MeshResponse(NodeManager.getMesh()));

                } else if (messageType == MessageType.Ping200) {

                    response = new Message(MessageType.PingResponse201, new PingResponse("hello, " +
                            IpUtil.addressAsString(message.getSourceIpAddress()) + "! v=" + Verifier.getVersion()));

                } else if (messageType == MessageType.UpdateRequest300) {

                    response = new Message(MessageType.UpdateResponse301, new UpdateResponse(message));

                } else if (messageType == MessageType.ResetRequest500) {

                    boolean success = ByteUtil.arraysAreEqual(message.getSourceNodeIdentifier(), Block.genesisVerifier);
                    String responseMessage;
                    if (success) {
                        responseMessage = "reset request accepted";
                        UpdateUtil.reset();
                    } else {
                        responseMessage = "source node identifier, " +
                                PrintUtil.compactPrintByteArray(message.getSourceNodeIdentifier()) + ", is not the " +
                                "Genesis verifier, " + PrintUtil.compactPrintByteArray(Block.genesisVerifier);
                    }

                    response = new Message(MessageType.ResetResponse501, new BooleanMessageResponse(success,
                            responseMessage));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            String errorMessage = e.getMessage();
            if (errorMessage == null) {
                errorMessage = "Message from exception is null.";
            }

            response = new Message(MessageType.Error65534, new ErrorMessage(errorMessage));
        }

        return response;
    }

    public static void updateStatus() {

        StatusResponse response = new StatusResponse();
        StringBuilder responseString = new StringBuilder();
        for (String line : response.getLines()) {
            responseString.append(line).append("\r\n");
        }

        int contentLength = responseString.toString().getBytes(StandardCharsets.UTF_8).length;
        StringBuilder headerString = new StringBuilder();
        headerString.append("HTTP/1.1 200 OK\r\n");
        headerString.append("Content-Type: text/plain; charset=utf-8\r\n");
        headerString.append("Cache-Control: no-cache\r\n");
        headerString.append("Connection: Close\r\n");
        headerString.append("Content-Length: ").append(contentLength).append("\r\n\r\n");
        headerString.append(responseString);

        statusBytes = headerString.toString().getBytes(StandardCharsets.UTF_8);
    }
}
