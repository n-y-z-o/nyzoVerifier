package co.nyzo.verifier;

import co.nyzo.verifier.messages.*;
import co.nyzo.verifier.messages.debug.*;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.SignatureUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Message {

    private static final long maximumMessageLength = 4194304;  // 4 MB
    private static final Set<ByteBuffer> whitelist = ConcurrentHashMap.newKeySet();
    private static final Set<MessageType> disallowedNonCycleTypes = new HashSet<>(Arrays.asList(MessageType.NewBlock9,
            MessageType.BlockVote19, MessageType.NewVerifierVote21, MessageType.MissingBlockVoteRequest23,
            MessageType.MissingBlockRequest25));
    public static final long replayProtectionInterval = 5000L;

    // We do not broadcast any messages to the full mesh from the broadcast method. We do, however, use the full mesh
    // as a potential pool for random requests for the following types. This reduces strain on in-cycle verifiers.
    private static final Set<MessageType> fullMeshMessageTypes = new HashSet<>(Arrays.asList(MessageType.BlockRequest11,
            MessageType.BlockWithVotesRequest37));

    static {
        loadWhitelist();
    }

    private long timestamp;  // millisecond precision -- when the message is first generated
    private MessageType type;
    private MessageObject content;
    private byte[] sourceNodeIdentifier;  // the identifier of the node that created this message
    private byte[] sourceNodeSignature;   // the signature of all preceding parts
    private boolean valid;       // not serialized
    private byte[] sourceIpAddress;   // not serialized

    // This is the constructor for a new message originating from this system.
    public Message(MessageType type, MessageObject content) {
        this.timestamp = System.currentTimeMillis();
        this.type = type;
        this.content = content;
        this.sourceNodeIdentifier = Verifier.getIdentifier();
        this.sourceNodeSignature = Verifier.sign(getBytesForSigning());
        this.valid = true;
    }

    // This is the constructor for a message from another system.
    public Message(long timestamp, MessageType type, MessageObject content, byte[] sourceNodeIdentifier,
                   byte[] sourceNodeSignature, byte[] sourceIpAddress) {

        this.timestamp = timestamp;
        this.type = type;
        this.content = content;
        this.sourceNodeIdentifier = sourceNodeIdentifier;
        this.sourceNodeSignature = sourceNodeSignature;
        this.sourceIpAddress = sourceIpAddress;

        // Verify the source signature.
        this.valid = SignatureUtil.signatureIsValid(sourceNodeSignature, getBytesForSigning(),
                sourceNodeIdentifier);
        if (!this.valid) {
            System.out.println("message from " + PrintUtil.compactPrintByteArray(sourceNodeIdentifier) + " of type " +
                    this.type + " is not valid, content is " + content);
            System.out.println("signature is " + ByteUtil.arrayAsStringWithDashes(sourceNodeSignature));
        }
    }

    public long getTimestamp() {
        return timestamp;
    }

    public MessageType getType() {
        return type;
    }

    public MessageObject getContent() {
        return content;
    }

    public byte[] getSourceNodeIdentifier() {
        return sourceNodeIdentifier;
    }

    public byte[] getSourceNodeSignature() {
        return sourceNodeSignature;
    }

    public boolean isValid() {
        return valid;
    }

    public byte[] getSourceIpAddress() {
        return sourceIpAddress;
    }

    public void sign(byte[] privateSeed) {
        this.sourceNodeIdentifier = KeyUtil.identifierForSeed(privateSeed);
        this.sourceNodeSignature = SignatureUtil.signBytes(getBytesForSigning(), privateSeed);
    }

    public static void broadcast(Message message) {

        System.out.println("broadcasting message: " + message.getType());

        // Send the message to all nodes in the current cycle and the top in the new-verifier queue.
        List<Node> mesh = NodeManager.getMesh();
        for (Node node : mesh) {
            if (node.isActive() && !ByteUtil.arraysAreEqual(node.getIdentifier(), Verifier.getIdentifier()) &&
                    BlockManager.verifierInOrNearCurrentCycle(ByteBuffer.wrap(node.getIdentifier()))) {
                String ipAddress = IpUtil.addressAsString(node.getIpAddress());
                fetch(ipAddress, node.getPort(), message, null);
            }
        }
    }

    public static void fetchFromRandomNode(Message message, MessageCallback messageCallback) {

        boolean isFullMeshMessage = fullMeshMessageTypes.contains(message.getType());

        Node node = null;
        List<Node> mesh = NodeManager.getMesh();
        Random random = new Random();
        while (node == null && !mesh.isEmpty()) {
            Node meshNode = mesh.remove(random.nextInt(mesh.size()));
            ByteBuffer nodeIdentifierBuffer = ByteBuffer.wrap(meshNode.getIdentifier());
            if (!ByteUtil.arraysAreEqual(meshNode.getIdentifier(), Verifier.getIdentifier()) && (isFullMeshMessage ||
                    BlockManager.verifierInCurrentCycle(nodeIdentifierBuffer) || !BlockManager.isCycleComplete())) {
                node = meshNode;
            }
        }

        if (node == null) {
            System.out.println("unable to find suitable node");
        } else {
            System.out.println("trying to fetch " + message.getType() + " from " +
                    NicknameManager.get(node.getIdentifier()));
            fetch(IpUtil.addressAsString(node.getIpAddress()), node.getPort(), message, messageCallback);
        }
    }

    public static void fetch(String hostNameOrIp, int port, Message message, MessageCallback messageCallback) {

        byte[] identifier = NodeManager.identifierForIpAddress(hostNameOrIp);

        // Do not send the message to this verifier, and do not send a message that will get this verifier blacklisted
        // if it is not in the cycle.
        if (!ByteUtil.arraysAreEqual(identifier, Verifier.getIdentifier()) &&
                (BlockManager.verifierInOrNearCurrentCycle(ByteBuffer.wrap(Verifier.getIdentifier())) ||
                        BlockManager.inGenesisCycle() ||
                        !disallowedNonCycleTypes.contains(message.getType()))) {

            new Thread(new Runnable() {
                @Override
                public void run() {
                    Socket socket = new Socket();
                    try {
                        socket.connect(new InetSocketAddress(hostNameOrIp, port), 3000);
                    } catch (Exception e) {
                        if (socket.isConnected()) {
                            try {
                                socket.close();
                            } catch (Exception ignored) { }
                        }
                        socket = null;
                    }

                    Message response = null;
                    if (socket == null) {
                        NodeManager.markFailedConnection(hostNameOrIp);
                    } else {
                        NodeManager.markSuccessfulConnection(hostNameOrIp);

                        try {
                            OutputStream outputStream = socket.getOutputStream();
                            outputStream.write(message.getBytesForTransmission());

                            response = readFromStream(socket.getInputStream(), socket.getInetAddress().getAddress(),
                                    message.getType());
                        } catch (Exception reportOnly) {
                            System.err.println("Exception sending message " + message.getType() + " to " +
                                    hostNameOrIp + ":" + port + ": " + PrintUtil.printException(reportOnly));
                        }

                        try {
                            socket.close();
                        } catch (Exception ignored) {
                            System.out.println("unable to close socket to " + hostNameOrIp + ":" + port);
                        }
                    }

                    if (messageCallback != null) {
                        if (response != null && response.isValid() &&
                                response.getTimestamp() >= System.currentTimeMillis() - replayProtectionInterval &&
                                response.getTimestamp() <= System.currentTimeMillis() + replayProtectionInterval) {
                            MessageQueue.add(messageCallback, response);
                        } else {
                            MessageQueue.add(messageCallback, null);
                        }
                    }
                }
            }, "Message-fetch-" + message).start();
        }
    }

    public static Message readFromStream(InputStream inputStream, byte[] sourceIpAddress, MessageType sourceType) {

        byte[] response = getResponse(inputStream);
        Message message;
        if (response.length == 0) {
            System.out.println("empty response from " + IpUtil.addressAsString(sourceIpAddress) + " for message of " +
                    "type " + sourceType);
            message = null;
        } else {
            message = fromBytes(response, sourceIpAddress);
        }

        return message;
    }

    private static byte[] getResponse(InputStream inputStream) {

        byte[] result = new byte[0];
        try {
            BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
            byte[] lengthBytes = new byte[4];
            bufferedInputStream.read(lengthBytes);
            int messageLength = ByteBuffer.wrap(lengthBytes).getInt();

            if (messageLength <= maximumMessageLength) {
                result = new byte[messageLength - 4];
                int totalBytesRead = 0;
                boolean readFailure = false;
                int waitCycles = 0;
                while (totalBytesRead < result.length && !readFailure && waitCycles < 10) {
                    int numberOfBytesRead = bufferedInputStream.read(result, totalBytesRead,
                            result.length - totalBytesRead);
                    if (numberOfBytesRead < 0) {
                        readFailure = true;
                    } else {
                        if (numberOfBytesRead == 0) {
                            waitCycles++;
                        }
                        totalBytesRead += numberOfBytesRead;
                    }

                    try {
                        Thread.sleep(10);
                    } catch (Exception ignore) {
                    }
                }

                if (totalBytesRead < result.length) {
                    System.err.println("only read " + totalBytesRead + " of " + result.length + " for message");
                }
            }

        } catch (Exception ignore) { }

        return result;
    }

    public byte[] getBytesForSigning() {

        // Determine the size (timestamp, type, source-node identifier, content if present).
        int sizeBytes = FieldByteSize.timestamp + FieldByteSize.messageType + FieldByteSize.identifier;
        if (content != null) {
            sizeBytes += content.getByteSize();
        }

        // Make the buffer.
        byte[] result = new byte[sizeBytes];
        ByteBuffer buffer = ByteBuffer.wrap(result);

        // Add the data.
        buffer.putLong(timestamp);
        buffer.putShort((short) type.getValue());
        if (content != null) {
            buffer.put(content.getBytes());
        }
        buffer.put(sourceNodeIdentifier);

        return result;
    }

    public byte[] getBytesForTransmission() {

        // Determine the size (timestamp, type, source-node identifier, source-node signature, content if present).
        int sizeBytes = FieldByteSize.messageLength + FieldByteSize.timestamp + FieldByteSize.messageType +
                FieldByteSize.identifier + FieldByteSize.signature;
        if (content != null) {
            sizeBytes += content.getByteSize();
        }

        // Make the buffer.
        byte[] result = new byte[sizeBytes];
        ByteBuffer buffer = ByteBuffer.wrap(result);

        // Add the size.
        buffer.putInt(sizeBytes);

        // Add the data.
        buffer.putLong(timestamp);
        buffer.putShort((short) type.getValue());
        if (content != null) {
            buffer.put(content.getBytes());
        }
        buffer.put(sourceNodeIdentifier);
        buffer.put(sourceNodeSignature);

        return result;
    }

    public static Message fromBytes(byte[] bytes, byte[] sourceIpAddress) {

        Message message = null;
        int typeValue = 0;
        MessageType type = null;
        try {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);

            // The size is discarded before this method, so it is not read here.

            long timestamp = buffer.getLong();
            typeValue = buffer.getShort() & 0xffff;
            type = MessageType.forValue(typeValue);

            MessageObject content = processContent(type, buffer);

            byte[] sourceNodeIdentifier = new byte[FieldByteSize.identifier];
            buffer.get(sourceNodeIdentifier);

            // If this is a non-cycle verifier sending disallowed messages, add it to the blacklist. Otherwise, build
            // the message.
            if (disallowedNonCycleTypes.contains(type) &&
                    !BlockManager.verifierInOrNearCurrentCycle(ByteBuffer.wrap(sourceNodeIdentifier)) &&
                    !ipIsWhitelisted(sourceIpAddress)) {

                BlacklistManager.addToBlacklist(sourceIpAddress);

            } else {
                byte[] sourceNodeSignature = new byte[FieldByteSize.signature];
                buffer.get(sourceNodeSignature);

                message = new Message(timestamp, type, content, sourceNodeIdentifier, sourceNodeSignature,
                        sourceIpAddress);
            }
        } catch (Exception reportOnly) {
            System.err.println("problem getting message from bytes, message type is " + typeValue + ", " +
                    type + ", " + PrintUtil.printException(reportOnly));
        }

        return message;
    }

    private static MessageObject processContent(MessageType type, ByteBuffer buffer) {

        MessageObject content = null;
        // Messages 1 and 2 are no longer used.
        if (type == MessageType.NodeJoin3) {
            content = NodeJoinMessage.fromByteBuffer(buffer);
        } else if (type == MessageType.NodeJoinResponse4) {
            content = NodeJoinResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.Transaction5) {
            content = Transaction.fromByteBuffer(buffer);
        } else if (type == MessageType.TransactionResponse6) {
            content = TransactionResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.PreviousHashResponse8) {
            content = PreviousHashResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.NewBlock9) {
            content = NewBlockMessage.fromByteBuffer(buffer);
        } else if (type == MessageType.BlockRequest11) {
            content = BlockRequest.fromByteBuffer(buffer);
        }  else if (type == MessageType.BlockResponse12) {
            content = BlockResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.TransactionPoolResponse14) {
            content = TransactionPoolResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.MeshResponse16) {
            content = MeshResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.StatusResponse18) {
            content = StatusResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.BlockVote19) {
            content = BlockVote.fromByteBuffer(buffer);
        } else if (type == MessageType.NewVerifierVote21) {
            content = NewVerifierVote.fromByteBuffer(buffer);
        } else if (type == MessageType.MissingBlockVoteRequest23) {
            content = MissingBlockVoteRequest.fromByteBuffer(buffer);
        } else if (type == MessageType.MissingBlockVoteResponse24) {
            content = BlockVote.fromByteBuffer(buffer);
        } else if (type == MessageType.MissingBlockRequest25) {
            content = MissingBlockRequest.fromByteBuffer(buffer);
        } else if (type == MessageType.MissingBlockResponse26) {
            content = MissingBlockResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.TimestampResponse28) {
            content = TimestampResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.HashVoteOverrideRequest29) {
            content = HashVoteOverrideRequest.fromByteBuffer(buffer);
        } else if (type == MessageType.HashVoteOverrideResponse30) {
            content = HashVoteOverrideResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.ConsensusThresholdOverrideRequest31) {
            content = ConsensusThresholdOverrideRequest.fromByteBuffer(buffer);
        } else if (type == MessageType.ConsensusThresholdOverrideResponse32) {
            content = ConsensusThresholdOverrideResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.NewVerifierVoteOverrideRequest33) {
            content = NewVerifierVoteOverrideRequest.fromByteBuffer(buffer);
        } else if (type == MessageType.NewVerifierVoteOverrideResponse34) {
            content = NewVerifierVoteOverrideResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.BootstrapRequestV2_35) {
            content = BootstrapRequest.fromByteBuffer(buffer);
        } else if (type == MessageType.BootstrapResponseV2_36) {
            content = BootstrapResponseV2.fromByteBuffer(buffer);
        } else if (type == MessageType.BlockWithVotesRequest37) {
            content = BlockWithVotesRequest.fromByteBuffer(buffer);
        } else if (type == MessageType.BlockWithVotesResponse38) {
            content = BlockWithVotesResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.VerifierRemovalVote39) {
            content = VerifierRemovalVote.fromByteBuffer(buffer);
        } else if (type == MessageType.FullMeshResponse42) {
            content = MeshResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.PingResponse201) {
            content = PingResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.UpdateResponse301) {
            content = UpdateResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.UnfrozenBlockPoolPurgeResponse405) {
            content = UnfrozenBlockPoolPurgeResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.UnfrozenBlockPoolStatusResponse407) {
            content = UnfrozenBlockPoolStatusResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.MeshStatusResponse409) {
            content = MeshStatusResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.ConsensusTallyStatusResponse413) {
            content = ConsensusTallyStatusResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.NewVerifierTallyStatusResponse415) {
            content = NewVerifierTallyStatusResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.BlacklistStatusResponse417) {
            content = BlacklistStatusResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.PerformanceScoreStatusResponse419) {
            content = PerformanceScoreStatusResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.VerifierRemovalTallyStatusResponse421) {
            content = VerifierRemovalTallyStatusResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.ResetResponse501) {
            content = BooleanMessageResponse.fromByteBuffer(buffer);
        } else if (type == MessageType.Error65534) {
            content = ErrorMessage.fromByteBuffer(buffer);
        }

        return content;
    }

    public static void putString(String value, ByteBuffer buffer) {

        if (value == null) {
            buffer.putShort((short) 0);
        } else {
            byte[] lineBytes = value.getBytes(StandardCharsets.UTF_8);
            buffer.putShort((short) lineBytes.length);
            buffer.put(lineBytes);
        }
    }

    public static String getString(ByteBuffer buffer) {

        int lineByteLength = buffer.getShort() & 0xffff;
        byte[] lineBytes = new byte[lineByteLength];
        buffer.get(lineBytes);

        return new String(lineBytes, StandardCharsets.UTF_8);
    }

    public static byte[] getByteArray(ByteBuffer buffer, int size) {

        byte[] array = new byte[size];
        buffer.get(array);

        return array;
    }

    private static void loadWhitelist() {

        Path path = Paths.get(Verifier.dataRootDirectory.getAbsolutePath() + "/whitelist");
        if (path.toFile().exists()) {
            try {
                List<String> contentsOfFile = Files.readAllLines(path);
                for (String line : contentsOfFile) {
                    line = line.trim();
                    int indexOfHash = line.indexOf("#");
                    if (indexOfHash >= 0) {
                        line = line.substring(0, indexOfHash).trim();
                    }
                    byte[] address = IpUtil.addressFromString(line);
                    if (address != null) {
                        whitelist.add(ByteBuffer.wrap(address));
                        System.out.println("added IP " + IpUtil.addressAsString(address) + " to whitelist");
                    }
                }
            } catch (Exception e) {
                System.out.println("issue getting whitelist: " + PrintUtil.printException(e));
            }
        } else {
            System.out.println("skipping whitelist loading; file not present");
        }
    }

    public static boolean ipIsWhitelisted(byte[] ipAddress) {

        return whitelist.contains(ByteBuffer.wrap(ipAddress));
    }

    @Override
    public String toString() {
        return "[Message: " + type + " (" + getContent() + ")]";
    }
}
