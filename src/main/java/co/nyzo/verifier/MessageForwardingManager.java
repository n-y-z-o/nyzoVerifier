package co.nyzo.verifier;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MessageForwardingManager {

    private static final long timeout = 10000L;

    private static final Map<ByteBuffer, Message> messageMap = new HashMap<>();

    public static synchronized void mergeRecipients(Message message) {

        ByteBuffer signature = ByteBuffer.wrap(message.getSourceNodeSignature());
        Message previousMessage = messageMap.get(signature);
        if (previousMessage != null) {
            message.getRecipients().putAll(previousMessage.getRecipients());
        }

        messageMap.put(signature, message);
    }

    public static synchronized void cleanMap() {

        long thresholdTimestamp = System.currentTimeMillis() - timeout;
        Set<Message> messages = new HashSet<>(messageMap.values());
        for (Message message : messages) {
            if (message.getTimestamp() < thresholdTimestamp) {
                messageMap.remove(ByteBuffer.wrap(message.getSourceNodeSignature()));
            }
        }
    }
}
