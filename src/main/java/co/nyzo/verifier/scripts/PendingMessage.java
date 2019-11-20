package co.nyzo.verifier.scripts;

import co.nyzo.verifier.MessageObject;
import co.nyzo.verifier.MessageType;
import co.nyzo.verifier.Node;

import java.util.concurrent.atomic.AtomicInteger;

public class PendingMessage {

    private Node recipient;
    private MessageType messageType;
    private MessageObject messageObject;
    private byte[] signerSeed;
    private AtomicInteger numberOfAttempts;
    private AtomicInteger numberOfSuccesses;
    private AtomicInteger numberOfFailures;

    public PendingMessage(Node recipient, MessageType messageType, MessageObject messageObject, byte[] signerSeed) {
        this.recipient = recipient;
        this.messageType = messageType;
        this.messageObject = messageObject;
        this.signerSeed = signerSeed;
        this.numberOfAttempts = new AtomicInteger(0);
        this.numberOfSuccesses = new AtomicInteger(0);
        this.numberOfFailures = new AtomicInteger(0);
    }

    public Node getRecipient() {
        return recipient;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public MessageObject getMessageObject() {
        return messageObject;
    }

    public byte[] getSignerSeed() {
        return signerSeed;
    }

    public int getNumberOfAttempts() {
        return numberOfAttempts.get();
    }

    public int incrementAndGetNumberOfAttempts() {
        return numberOfAttempts.incrementAndGet();
    }

    public int getNumberOfSuccesses() {
        return numberOfSuccesses.get();
    }

    public int incrementAndGetNumberOfSuccesses() {
        return numberOfSuccesses.incrementAndGet();
    }

    public int getNumberOfFailures() {
        return numberOfFailures.get();
    }

    public int incrementAndGetNumberOfFailures() {
        return numberOfFailures.incrementAndGet();
    }
}
