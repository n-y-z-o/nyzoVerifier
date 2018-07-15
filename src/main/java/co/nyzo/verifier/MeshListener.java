package co.nyzo.verifier;

import co.nyzo.verifier.messages.*;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PrintUtil;
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

        if (!alive.getAndSet(true)) {

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        serverSocket = new ServerSocket(standardPort);
                        port = serverSocket.getLocalPort();

                        while (!UpdateUtil.shouldTerminate()) {

                            if (Verifier.isPaused()) {
                                try {
                                    Thread.sleep(1000L);
                                } catch (Exception ignored) { }
                            } else {
                                try {
                                    Socket clientSocket = serverSocket.accept();
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {

                                            try {
                                                Message message = Message.readFromStream(clientSocket.getInputStream(),
                                                        IpUtil.addressFromString(clientSocket.getRemoteSocketAddress() +
                                                                ""), MessageType.IncomingRequest65533);

                                                // If the verifier is paused, do not process the message.
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
                                            } catch (Exception ignored) {
                                            }
                                        }
                                    }, "MeshListener-clientSocket").start();
                                } catch (Exception ignored) {
                                }
                            }
                        }

                        closeSocket();

                    } catch (Exception ignored) { }

                    alive.set(false);
                }
            }, "MeshListener-serverSocket").start();
        }
    }

    public static void closeSocket() {

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception ignored) {
            }
            serverSocket = null;
        }
    }

    public static Message response(Message message) {

        // This is the single point of dispatch for responding to all received messages.

        Message response = null;
        try {
            // Many actions are taken inside this block as a result of messages. Therefore, we only want to continue if
            // the message is valid.
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

                    NodeJoinMessage nodeJoinMessage = (NodeJoinMessage) message.getContent();
                    boolean isNewNode = NodeManager.updateNode(message.getSourceNodeIdentifier(),
                            message.getSourceIpAddress(), nodeJoinMessage.getPort());
                    NicknameManager.put(message.getSourceNodeIdentifier(), nodeJoinMessage.getNickname());

                    // If this is a new node, send a node join to make the connection in the other direction.
                    if (isNewNode) {
                        Message.fetch(IpUtil.addressAsString(message.getSourceIpAddress()), nodeJoinMessage.getPort(),
                                new Message(MessageType.NodeJoin3, null), null);
                    }

                    response = new Message(MessageType.NodeJoinResponse4, new NodeJoinResponse());

                } else if (messageType == MessageType.Transaction5) {

                    TransactionResponse responseContent = new TransactionResponse((Transaction) message.getContent());
                    response = new Message(MessageType.TransactionResponse6, responseContent);

                } else if (messageType == MessageType.PreviousHashRequest7) {

                    response = new Message(MessageType.PreviousHashResponse8, new PreviousHashResponse());

                } else if (messageType == MessageType.NewBlock9) {

                    // If this is a new node, send a node join to make the connection in the other direction.
                    NewBlockMessage blockMessage = (NewBlockMessage) message.getContent();
                    boolean isNewNode = NodeManager.updateNode(message.getSourceNodeIdentifier(),
                            message.getSourceIpAddress(), blockMessage.getPort());
                    if (isNewNode) {
                        Message.fetch(IpUtil.addressAsString(message.getSourceIpAddress()), blockMessage.getPort(),
                                new Message(MessageType.NodeJoin3, null), null);
                    }

                    UnfrozenBlockManager.registerBlock(blockMessage.getBlock());
                    response = new Message(MessageType.NewBlockResponse10, null);

                } else if (messageType == MessageType.BlockRequest11) {

                    BlockRequest request = (BlockRequest) message.getContent();
                    response = new Message(MessageType.BlockResponse12, new BlockResponse(request.getStartHeight(),
                            request.getEndHeight(), request.includeBalanceList()));

                } else if (messageType == MessageType.TransactionPoolRequest13) {

                    response = new Message(MessageType.TransactionPoolResponse14,
                            new TransactionPoolResponse(TransactionPool.allTransactions()));

                } else if (messageType == MessageType.MeshRequest15) {

                    response = new Message(MessageType.MeshResponse16, new MeshResponse(NodeManager.getMesh()));

                } else if (messageType == MessageType.StatusRequest17) {

                    response = new Message(MessageType.StatusResponse18, new StatusResponse());

                } else if (messageType == MessageType.BlockVote19) {

                    BlockVoteManager.registerVote(message.getSourceNodeIdentifier(), (BlockVote) message.getContent(),
                            false);
                    response = new Message(MessageType.BlockVoteResponse20, null);

                } else if (messageType == MessageType.NewVerifierVote21) {

                    NewVerifierVoteManager.registerVote(message.getSourceNodeIdentifier(),
                            (NewVerifierVote) message.getContent(), false);
                    response = new Message(MessageType.NewVerifierVoteResponse22, null);

                } else if (messageType == MessageType.MissingBlockVoteRequest23) {

                    MissingBlockVoteRequest request = (MissingBlockVoteRequest) message.getContent();
                    response = new Message(MessageType.MissingBlockVoteResponse24,
                            BlockVote.forHeight(request.getHeight()));

                } else if (messageType == MessageType.Ping200) {

                    response = new Message(MessageType.PingResponse201, new PingResponse("hello, " +
                            IpUtil.addressAsString(message.getSourceIpAddress()) + "! v=" + Version.getVersion()));

                } else if (messageType == MessageType.UpdateRequest300) {

                    response = new Message(MessageType.UpdateResponse301, new UpdateResponse(message));

                } else if (messageType == MessageType.UnfrozenBlockPoolPurgeRequest404) {

                    response = new Message(MessageType.UnfrozenBlockPoolPurgeResponse405,
                            new UnfrozenBlockPoolPurgeResponse(message));

                } else if (messageType == MessageType.UnfrozenBlockPoolStatusRequest406) {

                    response = new Message(MessageType.UnfrozenBlockPoolStatusResponse407,
                            new UnfrozenBlockPoolStatusResponse(message));

                } else if (messageType == MessageType.MeshStatusRequest408) {

                    response = new Message(MessageType.MeshStatusResponse409, new MeshStatusResponse(message));

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
                } else {

                    response = new Message(MessageType.Error65534, new ErrorMessage("unknown message type"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            String errorMessage = e.getMessage();
            if (errorMessage == null) {
                errorMessage = "message from exception is null";
            }

            response = new Message(MessageType.Error65534, new ErrorMessage(errorMessage));
        }

        return response;
    }
}
