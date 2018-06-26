package co.nyzo.verifier;

import co.nyzo.verifier.messages.*;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class MeshListener {

    private static final int numberOfThreads = 16;
    private static final Socket[] waitingSockets = new Socket[numberOfThreads * 1000];
    private static int waitingSocketsPutIndex = 0;
    private static int[] waitingSocketsGetIndices = new int[numberOfThreads];

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

            startServerSocketThread();

            for (int i = 0; i < numberOfThreads; i++) {
                startClientSocketThread(i);
            }
        }
    }

    private static void startServerSocketThread() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(standardPort);
                    port = serverSocket.getLocalPort();

                    while (!UpdateUtil.shouldTerminate()) {

                        try {
                            Socket clientSocket = serverSocket.accept();

                            if (waitingSockets[waitingSocketsPutIndex] != null) {
                                System.out.println("overwriting socket");
                            }
                            waitingSockets[waitingSocketsPutIndex] = clientSocket;
                            waitingSocketsPutIndex = (waitingSocketsPutIndex + 1) % waitingSockets.length;
                        } catch (Exception ignored) { }
                    }

                    closeSocket();

                } catch (Exception ignored) { }

                alive.set(false);
            }
        }, "MeshListener-serverSocket").start();
    }

    private static void startClientSocketThread(final int index) {

        waitingSocketsGetIndices[index] = index;

        new Thread(new Runnable() {
            @Override
            public void run() {

                long sumDistance = 0L;
                long sumSleep = 0L;
                int numberOfSleeps = 0;
                while (!UpdateUtil.shouldTerminate()) {

                    int getIndex = waitingSocketsGetIndices[index];
                    if (waitingSockets[getIndex] == null) {
                        try {
                            int distance = (getIndex + waitingSockets.length - waitingSocketsPutIndex) %
                                    waitingSockets.length;
                            sumDistance += distance;

                            long sleepTime = Math.min(1000L, Math.max(10L, distance *
                                    Verifier.oldestTimestampAge() / 10L));
                            sumSleep += sleepTime;
                            numberOfSleeps++;
                            Thread.sleep(sleepTime);
                        } catch (Exception ignored) { }
                    } else {
                        Socket clientSocket = waitingSockets[getIndex];

                        try {
                            Message message = Message.readFromStream(clientSocket.getInputStream(),
                                    IpUtil.addressFromString(clientSocket.getRemoteSocketAddress() +
                                            ""), MessageType.IncomingRequest65533);
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
                            Thread.sleep(3L);  // 3 milliseconds
                            clientSocket.close();
                        } catch (Exception ignored) { }

                        waitingSockets[getIndex] = null;
                        waitingSocketsGetIndices[index] = (getIndex + numberOfThreads) % waitingSockets.length;
                    }
                }

                System.out.println("average sleep for thread " + index + ": " + (sumSleep /
                        Math.max(1, numberOfSleeps)) + ", " + (sumDistance / Math.max(1, numberOfSleeps)) + ", " +
                        sumDistance);
            }
        }, "MeshListener-clientSocket" + index).start();
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

                    System.out.println("received node-join message");
                    NodeJoinMessage nodeJoinMessage = (NodeJoinMessage) message.getContent();
                    NodeManager.updateNode(message.getSourceNodeIdentifier(), message.getSourceIpAddress(),
                            nodeJoinMessage.getPort());

                    response = new Message(MessageType.NodeJoinResponse4, new NodeJoinResponse());

                } else if (messageType == MessageType.Transaction5) {

                    TransactionResponse responseContent = new TransactionResponse((Transaction) message.getContent());
                    response = new Message(MessageType.TransactionResponse6, responseContent);

                } else if (messageType == MessageType.PreviousHashRequest7) {

                    response = new Message(MessageType.PreviousHashResponse8, new PreviousHashResponse());

                } else if (messageType == MessageType.NewBlock9) {

                    UnfrozenBlockManager.registerBlock((Block) message.getContent());
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

                } else if (messageType == MessageType.Ping200) {

                    response = new Message(MessageType.PingResponse201, new PingResponse("hello, " +
                            IpUtil.addressAsString(message.getSourceIpAddress()) + "! v=" + Verifier.getVersion()));

                } else if (messageType == MessageType.UpdateRequest300) {

                    response = new Message(MessageType.UpdateResponse301, new UpdateResponse(message));

                } else if (messageType == MessageType.UnfrozenBlockPoolPurgeRequest404) {

                    response = new Message(MessageType.UnfrozenBlockPoolPurgeResponse405,
                            new UnfrozenBlockPoolPurgeResponse(message));

                } else if (messageType == MessageType.UnfrozenBlockPoolRequest406) {

                    response = new Message(MessageType.UnfrozenBlockPoolResponse407,
                            new UnfrozenBlockPoolResponse(message));

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
