package co.nyzo.verifier;

import co.nyzo.verifier.messages.*;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class MeshListener {

    public static void main(String[] args) {
        start();
    }

    private static final AtomicBoolean alive = new AtomicBoolean(false);

    public static boolean isAlive() {
        return alive.get();
    }

    public static final int standardPort = 9444;

    private static ServerSocket serverSocket = null;
    private static int port;

    public static int getPort() {
        return port;
    }

    public static void start() {

        final long startTimestamp = System.currentTimeMillis();
        if (!alive.getAndSet(true)) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        serverSocket = new ServerSocket(standardPort);
                        port = serverSocket.getLocalPort();

                        long timeToStartPort = System.currentTimeMillis() - startTimestamp;
                        System.out.println("actual port is " + port + ", took " + timeToStartPort + " ms to start");
                        while (!UpdateUtil.shouldTerminate()) {
                            Socket clientSocket = serverSocket.accept();

                            new Thread(new Runnable() {
                                @Override
                                public void run() {

                                    try {
                                        Message message = Message.readFromStream(clientSocket.getInputStream(),
                                                IpUtil.addressFromString(clientSocket.getRemoteSocketAddress() + ""));
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
                                        clientSocket.close();
                                    } catch (Exception ignored) { }
                                }
                            }, "MeshListener-clientSocket").start();
                        }

                        closeServerSocket();

                    } catch (Exception ignored) { }

                    alive.set(false);
                }
            }, "MeshListener-serverSocket").start();
        }
    }

    public static void closeServerSocket() {

        try {
            serverSocket.close();
        } catch (Exception ignored) { }
        serverSocket = null;
    }

    public static Message response(Message message) {

        // This is the single point of dispatch for responding to all received messages.

        Message response = null;
        String errorMessage = "";
        try {
            if (message != null && message.isValid()) {

                MessageType messageType = message.getType();
                System.out.println("message type is " + messageType);

                if (messageType == MessageType.NodeListRequest1) {

                    System.out.println("returning NodeListResponse");
                    NodeListRequest requestMessage = (NodeListRequest) message.getContent();
                    NodeManager.updateNode(message.getSourceNodeIdentifier(), message.getSourceIpAddress(),
                            requestMessage.getPort(), requestMessage.isFullNode());
                    response = new Message(MessageType.NodeListResponse2, new NodeListResponse(NodeManager.getMesh()));

                } else if (messageType == MessageType.NodeJoin3) {

                    NodeJoinMessage nodeJoinMessage = (NodeJoinMessage) message.getContent();
                    NodeManager.updateNode(message.getSourceNodeIdentifier(), message.getSourceIpAddress(),
                            nodeJoinMessage.getPort(), nodeJoinMessage.isFullNode());
                    response = new Message(MessageType.NodeJoinResponse4, new NodeJoinResponse());

                } else if (messageType == MessageType.Transaction5) {

                    response = new Message(MessageType.TransactionResponse6,
                            new TransactionResponse((Transaction) message.getContent()));

                    Message.forward(message);

                } else if (messageType == MessageType.PreviousHashRequest7) {

                    response = new Message(MessageType.PreviousHashResponse8, new PreviousHashResponse());

                } else if (messageType == MessageType.NewBlock9) {

                    System.out.println("message: " + message);
                    System.out.println("message content (should be block): " + message.getContent());
                    boolean shouldForwardBlock = ChainOptionManager.registerBlock((Block) message.getContent());

                    response = new Message(MessageType.NewBlockResponse10, null);

                    System.out.println("should forward block: " + shouldForwardBlock);
                    if (shouldForwardBlock) {
                        Message.forward(message);
                    }

                } else if (messageType == MessageType.TransactionPoolRequest13) {

                    response = new Message(MessageType.TransactionPoolResponse14,
                            new TransactionPoolResponse(TransactionPool.allTransactions()));

                } else if (messageType == MessageType.HighestBlockFrozenRequest15) {

                    response = new Message(MessageType.HighestBlockFrozenResponse16,
                            new HighestBlockFrozenResponse(BlockManager.highestBlockFrozen()));

                } else if (messageType == MessageType.Ping200) {

                    response = new Message(MessageType.PingResponse201, new PingResponse("hello, " +
                            IpUtil.addressAsString(message.getSourceIpAddress()) + "!"));

                } else if (messageType == MessageType.UpdateRequest300) {

                    response = new Message(MessageType.UpdateResponse301, new UpdateResponse(message));

                } else if (messageType == MessageType.GenesisBlock500) {

                    Block genesisBlock = (Block) message.getContent();
                    response = new Message(MessageType.GenesisBlockResponse501,
                            new GenesisBlockAcknowledgement(genesisBlock));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            errorMessage = e.getMessage();
            if (errorMessage == null) {
                errorMessage = "Message from exception is null.";
            }

            response = new Message(MessageType.Error65534, new ErrorMessage(errorMessage));
        }

        String sourceString = message == null ? "null" : IpUtil.addressAsString(message.getSourceIpAddress());
        System.out.println("response message for " + sourceString + " is " + response);

        return response;
    }

}
