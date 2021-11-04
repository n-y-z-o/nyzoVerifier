package co.nyzo.verifier;

import co.nyzo.verifier.client.ClientNodeManager;
import co.nyzo.verifier.messages.*;
import co.nyzo.verifier.messages.debug.*;
import co.nyzo.verifier.sentinel.Sentinel;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.LogUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.SignatureUtil;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
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
    private static final Set<MessageType> disallowedNonCycleTypes = new HashSet<>(Arrays.asList(MessageType.BlockVote19,
            MessageType.NewVerifierVote21, MessageType.MissingBlockVoteRequest23, MessageType.MissingBlockRequest25));
    private static final Set<MessageType> udpTypes = new HashSet<>(Arrays.asList(MessageType.BlockVote19,
            MessageType.NewVerifierVote21));
    public static final long replayProtectionInterval = 5000L;

    private static final Map<ByteBuffer, Long> dynamicWhitelist = new ConcurrentHashMap<>();
    public static final long dynamicWhitelistInterval = 1000L * 60L * 10L;  // 10 minutes

    private static boolean allowUnsafeMessages = false;

    private static DatagramSocket datagramSocket;
    static {
        try {
            datagramSocket = new DatagramSocket();
        } catch (Exception ignored) { }
    }

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

    // This is the constructor for a new message originating from this system not signed by the default verifier.
    public Message(MessageType type, MessageObject content, byte[] privateSeed) {
        this.timestamp = System.currentTimeMillis();
        this.type = type;
        this.content = content;
        this.sourceNodeIdentifier = KeyUtil.identifierForSeed(privateSeed);
        this.sourceNodeSignature = SignatureUtil.signBytes(getBytesForSigning(), privateSeed);
        this.valid = true;
    }

    // This is a constructor for a message from another system.
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
    }

    // This is a constructor for a message from another system.
    public Message(long timestamp, MessageType type, MessageObject content, byte[] sourceNodeIdentifier,
                   byte[] sourceNodeSignature, byte[] sourceIpAddress, boolean valid) {

        this.timestamp = timestamp;
        this.type = type;
        this.content = content;
        this.sourceNodeIdentifier = sourceNodeIdentifier;
        this.sourceNodeSignature = sourceNodeSignature;
        this.sourceIpAddress = sourceIpAddress;
        this.valid = valid;
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

        // Send the message to all nodes in the current cycle and the top in the new-verifier queue.
        Set<Node> nodes = BlockManager.getCurrentAndNearCycleNodes();
        System.out.println("broadcasting message: " + message.getType() + " to " + nodes.size());
        for (Node node : nodes) {
            if (node.isActive() && !ByteUtil.arraysAreEqual(node.getIdentifier(), Verifier.getIdentifier())) {
                fetch(node, message, null);
            }
        }
    }

    public static void fetchFromRandomNode(Message message, MessageCallback messageCallback) {

        Node node;
        switch (RunMode.getRunMode()) {
            case Client:
                node = ClientNodeManager.randomNode();
                break;
            case Sentinel:
                node = Sentinel.randomNode();
                break;
            case Verifier:
            default:
                node = randomNode(message.getType());
                break;
        }

        if (node == null) {
            System.out.println("unable to find suitable node for random fetch");
        } else {
            LogUtil.println("trying to fetch " + message.getType() + " from " +
                    NicknameManager.get(node.getIdentifier()));
            fetch(node, message, messageCallback);
        }
    }

    private static Node randomNode(MessageType messageType) {

        Node node = null;
        List<Node> mesh = NodeManager.getCycle();
        Random random = new Random();
        while (node == null && !mesh.isEmpty()) {
            Node meshNode = mesh.remove(random.nextInt(mesh.size()));
            ByteBuffer nodeIdentifierBuffer = ByteBuffer.wrap(meshNode.getIdentifier());
            if (!ByteUtil.arraysAreEqual(meshNode.getIdentifier(), Verifier.getIdentifier())) {
                node = meshNode;
            }
        }

        return node;
    }

    public static void fetch(Node node, Message message, MessageCallback messageCallback) {

        if (udpTypes.contains(message.getType()) && node.getPortUdp() > 0) {
            sendUdp(node.getIpAddress(), node.getPortUdp(), message);
        } else {
            fetchTcp(IpUtil.addressAsString(node.getIpAddress()), node.getPortTcp(), message, messageCallback);
        }
    }

    public static void fetchTcp(String hostNameOrIp, int port, Message message, MessageCallback messageCallback) {

        // Unless the option to allow unsafe messages is activated, do not send a message that might get this IP
        // blacklisted.
        if (allowUnsafeMessages ||
                BlockManager.verifierInOrNearCurrentCycle(ByteBuffer.wrap(message.getSourceNodeIdentifier())) ||
                        BlockManager.inGenesisCycle() || !disallowedNonCycleTypes.contains(message.getType())) {

            new Thread(new Runnable() {
                @Override
                public void run() {
                    Socket socket = new Socket();
                    try {
                        socket.connect(new InetSocketAddress(hostNameOrIp, port), 2000);
                    } catch (Exception e) {
                        if (socket.isConnected()) {
                            ConnectionManager.fastCloseSocket(socket);
                        }
                        socket = null;
                    }

                    Message response = null;
                    if (socket == null) {
                        NodeManager.markFailedConnection(hostNameOrIp);
                    } else {

                        try {
                            OutputStream outputStream = socket.getOutputStream();
                            outputStream.write(message.getBytesForTransmission());

                            socket.setSoTimeout(1000);
                            response = readFromStream(socket.getInputStream(), socket.getInetAddress().getAddress(),
                                    message.getType());
                            NodeManager.markSuccessfulConnection(hostNameOrIp);
                        } catch (Exception reportOnly) {
                            System.err.println("Exception sending message " + message.getType() + " to " +
                                    hostNameOrIp + ":" + port + ": " + PrintUtil.printException(reportOnly));
                        }

                        ConnectionManager.fastCloseSocket(socket);
                    }

                    if (messageCallback != null) {
                        if (response != null && response.isValid() &&
                                ((response.getTimestamp() >= System.currentTimeMillis() - replayProtectionInterval &&
                                        response.getTimestamp() <= System.currentTimeMillis() +
                                                replayProtectionInterval) ||
                                        response.getType() == MessageType.TimestampResponse28 ||
                                        response.getType() == MessageType.Error65534)) {
                            MessageQueue.add(messageCallback, response);
                        } else {
                            MessageQueue.add(messageCallback, null);
                        }
                    }
                }
            }, "Message-fetch-" + message).start();
        }
    }

    public static void sendUdp(byte[] ipAddress, int port, Message message) {

        byte[] identifier = NodeManager.identifierForIpAddress(ipAddress);

        // Do not send the message to this verifier, and do not send a message that will get this verifier blacklisted
        // if it is not in the cycle.
        if (!ByteUtil.arraysAreEqual(identifier, Verifier.getIdentifier()) &&
                (BlockManager.verifierInOrNearCurrentCycle(ByteBuffer.wrap(Verifier.getIdentifier())) ||
                        BlockManager.inGenesisCycle() ||
                        !disallowedNonCycleTypes.contains(message.getType()))) {

            try {
                byte[] messageBytes = message.getBytesForTransmission();
                InetAddress address = Inet4Address.getByAddress(ipAddress);
                DatagramPacket packet = new DatagramPacket(messageBytes, messageBytes.length, address, port);
                datagramSocket.send(packet);
            } catch (Exception ignored) { }
        }
    }

    public static Message readFromStream(InputStream inputStream, byte[] sourceIpAddress, MessageType sourceType) {

        byte[] response = getResponse(inputStream);
        Message message;
        if (response.length == 0) {
            message = null;
        } else {
            message = fromBytes(response, sourceIpAddress, false);
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

    public static Message fromBytes(byte[] bytes, byte[] sourceIpAddress, boolean isUdp) {

        Message message = null;
        int typeValue = 0;
        MessageType type = null;
        try {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);

            // For UDP, the length is still in the buffer. For TCP, the array is sized to the message.
            int bufferLength = isUdp ? buffer.getInt() : bytes.length;

            long timestamp = buffer.getLong();
            typeValue = buffer.getShort() & 0xffff;
            type = MessageType.forValue(typeValue);

            // Read the source-node identifier from the end of the buffer.
            int contentStartPosition = buffer.position();
            buffer.position(bufferLength - FieldByteSize.identifier - FieldByteSize.signature);
            byte[] sourceNodeIdentifier = getByteArray(buffer, FieldByteSize.identifier);

            // If this is a non-cycle verifier sending disallowed messages, add it to the blacklist. Otherwise, build
            // the message.
            if (disallowedNonCycleTypes.contains(type) &&
                    !BlockManager.verifierInOrNearCurrentCycle(ByteBuffer.wrap(sourceNodeIdentifier)) &&
                    !ipIsWhitelisted(sourceIpAddress)) {

                // Only add the IP to the blacklist if this is a TCP message. IP addresses can be spoofed for UDP
                // messages. To allow room for sentinels to send new blocks without concern for blacklisting, new-block
                // messages from unexpected senders are discarded but do not result in blacklisting.
                if (!isUdp && type != MessageType.NewBlock9) {
                    BlacklistManager.addToBlacklist(sourceIpAddress);
                }
            } else {
                // If the signature is valid, continue to processing the content.
                byte[] sourceNodeSignature = getByteArray(buffer, FieldByteSize.signature);
                int signedBytesStart = isUdp ? 4 : 0;
                int signedBytesEnd = bufferLength - FieldByteSize.signature;
                boolean signatureIsValid = SignatureUtil.signatureIsValid(sourceNodeSignature, bytes,
                        sourceNodeIdentifier, signedBytesStart, signedBytesEnd);

                if (signatureIsValid) {
                    // Process the message content.
                    buffer.position(contentStartPosition);
                    MessageObject content = processContent(type, buffer, sourceNodeIdentifier);

                    // Build the message.
                    message = new Message(timestamp, type, content, sourceNodeIdentifier, sourceNodeSignature,
                            sourceIpAddress, signatureIsValid);
                } else {
                    // Build a message with the information that is available. The content will not be used for invalid
                    // messages, so processing the content would be wasteful.
                    message = new Message(timestamp, type, null, sourceNodeIdentifier, sourceNodeSignature,
                            sourceIpAddress, signatureIsValid);
                }
            }
        } catch (Exception reportOnly) {
            System.err.println("problem getting message from bytes, message type is " + typeValue + ", " +
                    type + ", " + PrintUtil.printException(reportOnly));
        }

        return message;
    }

    private static MessageObject processContent(MessageType type, ByteBuffer buffer, byte[] sourceNodeIdentifier) {

        switch (type) {
            // Messages 1, 2, 3, and 4 are no longer used.
            case Transaction5:
                return Transaction.fromByteBuffer(buffer);
            case TransactionResponse6:
                return TransactionResponse.fromByteBuffer(buffer);
            case PreviousHashResponse8:
                return PreviousHashResponse.fromByteBuffer(buffer);
            case NewBlock9:
                return NewBlockMessage.fromByteBuffer(buffer, sourceNodeIdentifier);
            case BlockRequest11:
                return BlockRequest.fromByteBuffer(buffer);
            case BlockResponse12:
                return BlockResponse.fromByteBuffer(buffer);
            case TransactionPoolResponse14:
                return TransactionListResponse.fromByteBuffer(buffer);
            case MeshResponse16:
                return MeshResponse.fromByteBuffer(buffer);
            case StatusResponse18:
                return StatusResponse.fromByteBuffer(buffer);
            case BlockVote19:
                return BlockVote.fromByteBuffer(buffer);
            case NewVerifierVote21:
                return NewVerifierVote.fromByteBuffer(buffer);
            case MissingBlockVoteRequest23:
                return MissingBlockVoteRequest.fromByteBuffer(buffer);
            case MissingBlockVoteResponse24:
                return BlockVote.fromByteBuffer(buffer);
            case MissingBlockRequest25:
                return MissingBlockRequest.fromByteBuffer(buffer);
            case MissingBlockResponse26:
                return MissingBlockResponse.fromByteBuffer(buffer);
            case TimestampResponse28:
                return TimestampResponse.fromByteBuffer(buffer);
            case HashVoteOverrideRequest29:
                return HashVoteOverrideRequest.fromByteBuffer(buffer);
            case HashVoteOverrideResponse30:
                return HashVoteOverrideResponse.fromByteBuffer(buffer);
            case ConsensusThresholdOverrideRequest31:
                return ConsensusThresholdOverrideRequest.fromByteBuffer(buffer);
            case ConsensusThresholdOverrideResponse32:
                return ConsensusThresholdOverrideResponse.fromByteBuffer(buffer);
            case NewVerifierVoteOverrideRequest33:
                return NewVerifierVoteOverrideRequest.fromByteBuffer(buffer);
            case NewVerifierVoteOverrideResponse34:
                return NewVerifierVoteOverrideResponse.fromByteBuffer(buffer);
            case BootstrapRequestV2_35:
                return BootstrapRequest.fromByteBuffer(buffer);
            case BootstrapResponseV2_36:
                return BootstrapResponseV2.fromByteBuffer(buffer);
            case BlockWithVotesRequest37:
                return BlockWithVotesRequest.fromByteBuffer(buffer);
            case BlockWithVotesResponse38:
                return BlockWithVotesResponse.fromByteBuffer(buffer);
            case VerifierRemovalVote39:
                return VerifierRemovalVote.fromByteBuffer(buffer);
            case FullMeshResponse42:
                return MeshResponse.fromByteBuffer(buffer);
            case NodeJoinV2_43:
                return NodeJoinMessageV2.fromByteBuffer(buffer);
            case NodeJoinResponseV2_44:
                return NodeJoinResponseV2.fromByteBuffer(buffer);
            case FrozenEdgeBalanceListResponse46:
                return BalanceListResponse.fromByteBuffer(buffer);
            case MinimalBlock51:
                return MinimalBlock.fromByteBuffer(buffer);
            case IpAddressResponse54:
                return IpAddressMessageObject.fromByteBuffer(buffer);
            case PingResponse201:
                return PingResponse.fromByteBuffer(buffer);
            case UpdateResponse301:
                return UpdateResponse.fromByteBuffer(buffer);
            case UnfrozenBlockPoolPurgeResponse405:
                return UnfrozenBlockPoolPurgeResponse.fromByteBuffer(buffer);
            case UnfrozenBlockPoolStatusResponse407:
                return UnfrozenBlockPoolStatusResponse.fromByteBuffer(buffer);
            case MeshStatusResponse409:
                return MeshStatusResponse.fromByteBuffer(buffer);
            case ConsensusTallyStatusResponse413:
                return ConsensusTallyStatusResponse.fromByteBuffer(buffer);
            case NewVerifierTallyStatusResponse415:
                return NewVerifierTallyStatusResponse.fromByteBuffer(buffer);
            case BlacklistStatusResponse417:
                return BlacklistStatusResponse.fromByteBuffer(buffer);
            case PerformanceScoreStatusResponse419:
                return PerformanceScoreStatusResponse.fromByteBuffer(buffer);
            case VerifierRemovalTallyStatusResponse421:
                return VerifierRemovalTallyStatusResponse.fromByteBuffer(buffer);
            case BlockDelayResponse423:
                return BlockDelayResponse.fromByteBuffer(buffer);
            case WhitelistRequest424:
                return IpAddressMessageObject.fromByteBuffer(buffer);
            case WhitelistResponse425:
                return WhitelistResponse.fromByteBuffer(buffer);
            case ResetResponse501:
                return BooleanMessageResponse.fromByteBuffer(buffer);
            case Error65534:
                return ErrorMessage.fromByteBuffer(buffer);
            default:
                return null;
        }
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

    public static void putString(String value, ByteBuffer buffer, int maximumStringByteLength) {

        if (value == null) {
            buffer.putShort((short) 0);
        } else {
            byte[] lineBytes = value.getBytes(StandardCharsets.UTF_8);
            if (lineBytes.length > maximumStringByteLength) {
                lineBytes = Arrays.copyOf(lineBytes, maximumStringByteLength);
            }
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

    public static String getString(ByteBuffer buffer, int maximumStringByteLength) {

        int lineByteLength = Math.min(buffer.getShort() & 0xffff, maximumStringByteLength);
        byte[] lineBytes = new byte[lineByteLength];
        buffer.get(lineBytes);

        return new String(lineBytes, StandardCharsets.UTF_8);
    }

    public static byte[] getByteArray(ByteBuffer buffer, int size) {

        byte[] array = new byte[size];
        buffer.get(array);

        return array;
    }

    public static byte[] getByteArray(RandomAccessFile file, int size) {
        byte[] array = new byte[size];
        try {
            int totalBytesRead = 0;
            int bytesRead = 0;
            while (bytesRead >= 0 && totalBytesRead < size) {
                bytesRead = file.read(array);
                if (bytesRead > 0) {
                    totalBytesRead += bytesRead;
                }
            }
        } catch (Exception ignored) { }

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
        ByteBuffer ipAddressBuffer = ByteBuffer.wrap(ipAddress);
        return whitelist.contains(ipAddressBuffer) || dynamicWhitelist.keySet().contains(ipAddressBuffer);
    }

    public static void whitelistIpAddress(byte[] ipAddress) {
        dynamicWhitelist.put(ByteBuffer.wrap(ipAddress), System.currentTimeMillis());
        LogUtil.println("added " + IpUtil.addressAsString(ipAddress) + " to dynamic whitelist");
    }

    public static void performMaintenance() {
        // Remove stale entries from the dynamic whitelist.
        Set<ByteBuffer> ipAddresses = new HashSet<>(dynamicWhitelist.keySet());
        long threshold = System.currentTimeMillis() - dynamicWhitelistInterval;
        for (ByteBuffer ipAddress : ipAddresses) {
            if (dynamicWhitelist.getOrDefault(ipAddress, 0L) < threshold) {
                dynamicWhitelist.remove(ipAddress);
                LogUtil.println("removed " + IpUtil.addressAsString(ipAddress.array()) + " from dynamic whitelist");
            }
        }
    }

    public static void setAllowUnsafeMessages(boolean allowUnsafeMessages) {
        Message.allowUnsafeMessages = allowUnsafeMessages;
    }

    @Override
    public String toString() {
        return "[Message: " + type + " (" + getContent() + ")]";
    }
}
