package co.nyzo.verifier;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MessageForwardingManager {

    private static final long timeout = 10000L;

    private static final Map<ByteBuffer, Message> messageMap = new HashMap<>();
    private static final Map<ByteBuffer, Set<ByteBuffer>> messageSignatureToRecipientMap = new HashMap<>();

    public static synchronized boolean alreadySentMessage(Message message, byte[] identifier) {

        ByteBuffer signature = ByteBuffer.wrap(message.getSourceNodeSignature());
        Set<ByteBuffer> recipients = messageSignatureToRecipientMap.get(signature);
        if (recipients == null) {
            recipients = new HashSet<>();
            messageSignatureToRecipientMap.put(signature, recipients);
        }

        ByteBuffer identifierBuffer = ByteBuffer.wrap(identifier);
        boolean alreadySent = recipients.contains(identifierBuffer);
        recipients.add(identifierBuffer);

        return alreadySent;
    }

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
                ByteBuffer signature = ByteBuffer.wrap(message.getSourceNodeSignature());
                messageMap.remove(signature);
                messageSignatureToRecipientMap.remove(signature);
            }
        }
    }
}
