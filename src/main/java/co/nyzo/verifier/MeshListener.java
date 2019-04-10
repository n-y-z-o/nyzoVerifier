package co.nyzo.verifier;

import co.nyzo.verifier.messages.*;
import co.nyzo.verifier.messages.debug.*;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

public class MeshListener {

    private static final AtomicLong numberOfMessagesRejected = new AtomicLong(0);
    private static final AtomicLong numberOfMessagesAccepted = new AtomicLong(0);

    private static final int maximumConcurrentConnectionsForIp = 20;

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

    private static final BiFunction<Integer, Integer, Integer> mergeFunction =
            (integer0, integer1) -> {
                int value0 = integer0 == null ? 0 : integer0;
                int value1 = integer1 == null ? 0 : integer1;
                return value0 + value1;
            };

    public static void start() {

        Map<ByteBuffer, Integer> connectionsPerIp = new ConcurrentHashMap<>();
        AtomicInteger activeReadThreads = new AtomicInteger(0);

        if (!alive.getAndSet(true)) {

            new Thread(() -> {
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
                                processSocket(clientSocket, activeReadThreads, connectionsPerIp);
                            } catch (Exception ignored) { }
                        }
                    }

                    closeSocket();

                } catch (Exception e) {

                    System.err.println("Exception trying to open mesh listener. Exiting.");
                    UpdateUtil.terminate();
                }

                alive.set(false);
            }, "MeshListener-serverSocket").start();
        }
    }

    private static void processSocket(Socket clientSocket, AtomicInteger activeReadThreads,
                                      Map<ByteBuffer, Integer> connectionsPerIp) {

        byte[] ipAddress = clientSocket.getInetAddress().getAddress();
        if (BlacklistManager.inBlacklist(ipAddress)) {
            try {
                numberOfMessagesRejected.incrementAndGet();
                clientSocket.close();
            } catch (Exception ignored) { }
        } else {
            ByteBuffer ipBuffer = ByteBuffer.wrap(ipAddress);
            int connectionsForIp = connectionsPerIp.merge(ipBuffer, 1, mergeFunction);

            if (connectionsForIp > maximumConcurrentConnectionsForIp && Message.ipIsWhitelisted(ipAddress)) {

                System.out.println("blacklisting IP " + IpUtil.addressAsString(ipAddress) +
                        " due to too many concurrent connections");

                // Decrement the counter, add the IP to the blacklist, and close the socket without responding.
                connectionsPerIp.merge(ipBuffer, -1, mergeFunction);
                BlacklistManager.addToBlacklist(ipAddress);
                try {
                    clientSocket.close();
                } catch (Exception ignored) { }

            } else {

                // Read the message and respond.
                numberOfMessagesAccepted.incrementAndGet();
                activeReadThreads.incrementAndGet();
                new Thread(() -> {

                    try {
                        clientSocket.setSoTimeout(300);
                        readMessageAndRespond(clientSocket);
                    } catch (Exception ignored) { }

                    // Decrement the counter for this IP.
                    connectionsPerIp.merge(ipBuffer, -1, mergeFunction);

                    if (activeReadThreads.decrementAndGet() == 0) {

                        // When the number of active threads is zero, clear the map of
                        // connections per IP to prevent accumulation of too many IP
                        // addresses over time.
                        connectionsPerIp.clear();
                    }
                }, "MeshListener-clientSocket").start();
            }
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

        } catch (Exception ignored) { }

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

    private static Message response(Message message) {

        // This is the single point of dispatch for responding to all received messages.

        Message response = null;
        try {
            // Many actions are taken inside this block as a result of messages. Therefore, we only want to continue if
            // the message is valid. The timestamp check protects against various replay attacks.
            if (message != null && message.isValid() &&
                    message.getTimestamp() >= System.currentTimeMillis() - Message.replayProtectionInterval &&
                    message.getTimestamp() <= System.currentTimeMillis() + Message.replayProtectionInterval) {

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

                    response = new Message(MessageType.MeshResponse16, new MeshResponse(NodeManager.getCycle()));

                } else if (messageType == MessageType.StatusRequest17) {

                    response = new Message(MessageType.StatusResponse18,
                            new StatusResponse(message.getSourceNodeIdentifier()));

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

                } else if (messageType == MessageType.VerifierRemovalVote39) {

                    VerifierRemovalManager.registerVote(message.getSourceNodeIdentifier(),
                            (VerifierRemovalVote) message.getContent());
                    response = new Message(MessageType.VerifierRemovalVoteResponse40, null);

                } else if (messageType == MessageType.FullMeshRequest41) {

                    response = new Message(MessageType.FullMeshResponse42, new MeshResponse(NodeManager.getMesh()));

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

                } else if (messageType == MessageType.PerformanceScoreStatusRequest418) {

                    response = new Message(MessageType.PerformanceScoreStatusResponse419,
                            new PerformanceScoreStatusResponse(message));

                } else if (messageType == MessageType.VerifierRemovalTallyStatusRequest420) {

                    response = new Message(MessageType.VerifierRemovalTallyStatusResponse421,
                            new VerifierRemovalTallyStatusResponse(message));

                } else if (messageType == MessageType.ResetRequest500) {

                    boolean success = ByteUtil.arraysAreEqual(message.getSourceNodeIdentifier(),
                            Verifier.getIdentifier());
                    String responseMessage;
                    if (success) {
                        responseMessage = "reset request accepted";
                        UpdateUtil.reset();
                    } else {
                        responseMessage = "source node identifier, " +
                                PrintUtil.compactPrintByteArray(message.getSourceNodeIdentifier()) + ", is not the " +
                                "local verifier, " + PrintUtil.compactPrintByteArray(Verifier.getIdentifier());
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

        return numberOfMessagesRejected.get();
    }

    public static long getNumberOfMessagesAccepted() {

        return numberOfMessagesAccepted.get();
    }
}
