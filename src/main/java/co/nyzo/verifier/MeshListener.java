package co.nyzo.verifier;

import co.nyzo.verifier.messages.*;
import co.nyzo.verifier.messages.debug.*;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class MeshListener {

    private static long numberOfMessagesRejected = 0;
    private static long numberOfMessagesAccepted = 0;

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
                                    if (BlacklistManager.inBlacklist(clientSocket.getInetAddress().getAddress())) {
                                        try {
                                            numberOfMessagesRejected++;
                                            clientSocket.close();
                                        } catch (Exception ignored) { }
                                    } else {
                                        numberOfMessagesAccepted++;
                                        new Thread(new Runnable() {
                                            @Override
                                            public void run() {

                                                readMessageAndRespond(clientSocket);
                                            }
                                        }, "MeshListener-clientSocket").start();
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                        }

                        closeSocket();

                    } catch (Exception e) {

                        System.err.println("Exception trying to open mesh listener. Exiting.");
                        UpdateUtil.terminate();
                    }

                    alive.set(false);
                }
            }, "MeshListener-serverSocket").start();
        }
    }

    private static void readMessageAndRespond(Socket clientSocket) {

        try {
            Message message = Message.readFromStream(clientSocket.getInputStream(),
                    IpUtil.addressFromString(clientSocket.getRemoteSocketAddress() + ""),
                    MessageType.IncomingRequest65533);

            if (message != null) {
                Message response = response(message);
                if (response != null) {
                    clientSocket.getOutputStream().write(response.getBytesForTransmission());
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

                if (messageType == MessageType.NodeJoin3) {

                    NodeManager.updateNode(message);

                    NodeJoinMessage nodeJoinMessage = (NodeJoinMessage) message.getContent();
                    NicknameManager.put(message.getSourceNodeIdentifier(), nodeJoinMessage.getNickname());

                    response = new Message(MessageType.NodeJoinResponse4, new NodeJoinResponse());

                } else if (messageType == MessageType.Transaction5) {

                    TransactionResponse responseContent = new TransactionResponse((Transaction) message.getContent());
                    response = new Message(MessageType.TransactionResponse6, responseContent);

                } else if (messageType == MessageType.PreviousHashRequest7) {

                    response = new Message(MessageType.PreviousHashResponse8, new PreviousHashResponse());

                } else if (messageType == MessageType.NewBlock9) {

                    NewBlockMessage blockMessage = (NewBlockMessage) message.getContent();
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

                    BlockVoteManager.registerVote(message);
                    response = new Message(MessageType.BlockVoteResponse20, null);

                } else if (messageType == MessageType.NewVerifierVote21) {

                    NewVerifierVoteManager.registerVote(message.getSourceNodeIdentifier(),
                            (NewVerifierVote) message.getContent(), false);
                    response = new Message(MessageType.NewVerifierVoteResponse22, null);

                } else if (messageType == MessageType.MissingBlockVoteRequest23) {

                    MissingBlockVoteRequest request = (MissingBlockVoteRequest) message.getContent();
                    response = new Message(MessageType.MissingBlockVoteResponse24,
                            BlockVote.forHeight(request.getHeight()));

                } else if (messageType == MessageType.MissingBlockRequest25) {

                    MissingBlockRequest request = (MissingBlockRequest) message.getContent();
                    response = new Message(MessageType.MissingBlockResponse26,
                            new MissingBlockResponse(request.getHeight(), request.getHash()));

                } else if (messageType == MessageType.TimestampRequest27) {

                    response = new Message(MessageType.TimestampResponse28, new TimestampResponse());

                } else if (messageType == MessageType.HashVoteOverrideRequest29) {

                    response = new Message(MessageType.HashVoteOverrideResponse30,
                            new HashVoteOverrideResponse(message));

                } else if (messageType == MessageType.ConsensusThresholdOverrideRequest31) {

                    response = new Message(MessageType.ConsensusThresholdOverrideResponse32,
                            new ConsensusThresholdOverrideResponse(message));

                } else if (messageType == MessageType.NewVerifierVoteOverrideRequest33) {

                    response = new Message(MessageType.NewVerifierVoteOverrideResponse34,
                            new NewVerifierVoteOverrideResponse(message));

                } else if (messageType == MessageType.BootstrapRequestV2_35) {

                    response = new Message(MessageType.BootstrapResponseV2_36, new BootstrapResponseV2());

                } else if (messageType == MessageType.BlockWithVotesRequest37) {

                    long height = ((BlockWithVotesRequest) message.getContent()).getHeight();
                    response = new Message(MessageType.BlockWithVotesResponse38, new BlockWithVotesResponse(height));

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

                } else if (messageType == MessageType.ConsensusTallyStatusRequest412) {

                    response = new Message(MessageType.ConsensusTallyStatusResponse413,
                            new ConsensusTallyStatusResponse(message));

                } else if (messageType == MessageType.NewVerifierTallyStatusRequest414) {

                    response = new Message(MessageType.NewVerifierTallyStatusResponse415,
                            new NewVerifierTallyStatusResponse(message));

                } else if (messageType == MessageType.BlacklistStatusRequest416) {

                    response = new Message(MessageType.BlacklistStatusResponse417,
                            new BlacklistStatusResponse(message));

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

    public static long getNumberOfMessagesRejected() {

        return numberOfMessagesRejected;
    }

    public static long getNumberOfMessagesAccepted() {

        return numberOfMessagesAccepted;
    }
}
