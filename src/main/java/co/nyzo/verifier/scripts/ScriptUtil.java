package co.nyzo.verifier.scripts;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.CommandOutput;
import co.nyzo.verifier.messages.MeshResponse;
import co.nyzo.verifier.messages.MultilineTextResponse;
import co.nyzo.verifier.util.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ScriptUtil {

    public static int maximumInFlightRequests = PreferencesUtil.getInt("script_maximum_in_flight_requests", 50);
    public static int maximumMessageAttempts = PreferencesUtil.getInt("script_maximum_message_attempts", 3);

    public static List<byte[]> ipAddressesForVerifier(byte[] identifier) {  
        
        // Ask Nyzo verifier 0 for the mesh. Get the IP addresses of the verifier.
        List<byte[]> ipAddresses = new ArrayList<>();  
        Message meshRequest = new Message(MessageType.MeshRequest15, null);  
        AtomicBoolean receivedResponse = new AtomicBoolean(false);  
        Message.fetchTcp("verifier0.nyzo.co", MeshListener.standardPortTcp, meshRequest, new MessageCallback() {
            
            @Override  
            public void responseReceived(Message message) {  
                
                MeshResponse meshResponse = (MeshResponse) message.getContent();  
                for (Node node : meshResponse.getMesh()) {  
                    if (ByteUtil.arraysAreEqual(identifier, node.getIdentifier())) {  
                        ipAddresses.add(node.getIpAddress());  
                    }  
                    
                    receivedResponse.set(true);  
                }  
            }  
        });  
        
        // Wait for the response to return.  
        while (!receivedResponse.get()) {  
            try {  
                Thread.sleep(300L);  
            } catch (Exception ignored) { }  
        }  
        
        if (ipAddresses.isEmpty()) {  
            System.out.println("unable to find IP addresses for identifier " +  
                    PrintUtil.compactPrintByteArray(identifier));  
        } else {  
            for (byte[] ipAddress : ipAddresses) {  
                System.out.println("found IP address: " + IpUtil.addressAsString(ipAddress));  
            }  
        }  
        
        return ipAddresses;  
    }

    public static void fetchMultilineStatus(MessageType messageType, String[] args) {

        // Get the private seed. If none is provided, we will send a request to the loopback address.
        byte[] privateSeed;
        if (args.length < 1) {
            System.out.println("*** no in-cycle verifier seed provided; using local verifier seed ***");
            privateSeed = null;
        } else {
            privateSeed = ByteUtil.byteArrayFromHexString(args[0], FieldByteSize.seed);
        }

        // Get the corresponding identifier.
        byte[] inCycleVerifierIdentifier = privateSeed == null ? null : KeyUtil.identifierForSeed(privateSeed);

        // Get the IP addresses of the verifier.
        List<byte[]> ipAddresses = inCycleVerifierIdentifier == null ?
                Collections.singletonList(IpUtil.addressFromString("127.0.0.1")) :
                ipAddressesForVerifier(inCycleVerifierIdentifier);
        if (ipAddresses.isEmpty()) {
            System.out.println("unable to find IP address of " +
                    ByteUtil.arrayAsStringWithDashes(inCycleVerifierIdentifier));
        }

        // Send the request to our verifier instances.
        AtomicInteger numberOfResponsesNotYetReceived = new AtomicInteger(ipAddresses.size());
        Message message = new Message(messageType, null);
        if (privateSeed != null) {
            message.sign(privateSeed);
        }
        for (byte[] ipAddress : ipAddresses) {
            Message.fetchTcp(IpUtil.addressAsString(ipAddress), MeshListener.standardPortTcp, message,
                    new MessageCallback() {
                        @Override
                        public void responseReceived(Message message) {

                            System.out.println("response message: " + message);
                            if (message != null) {
                                if (message.getContent() instanceof MultilineTextResponse) {
                                    MultilineTextResponse response = (MultilineTextResponse) message.getContent();
                                    System.out.println("response number of lines: " + response.getLines().size());
                                    for (String line : response.getLines()) {
                                        System.out.println(line);
                                    }
                                } else {
                                    System.out.println("content is incorrect type: " + message.getContent());
                                }
                            }

                            numberOfResponsesNotYetReceived.decrementAndGet();
                        }
            });
        }

        // Wait for the responses to return.
        while (numberOfResponsesNotYetReceived.get() > 0) {
            try {
                Thread.sleep(300L);
            } catch (Exception ignored) { }
        }

        // Terminate the application.
        UpdateUtil.terminate();
    }

    public static void primeMessageQueue() {
        Message.fetchTcp("127.0.0.1", MeshListener.getPortTcp(), new Message(MessageType.Ping200, null), null);
    }

    public static void sendMessages(Set<PendingMessage> messages, CommandOutput output) {

        output.println("need to send " + messages.size() + " messages");
        boolean done = false;
        AtomicInteger numberOfInFlightRequests = new AtomicInteger(0);
        int iteration = 0;
        AtomicInteger numberOfSuccessfulMessages = new AtomicInteger(0);
        AtomicInteger numberOfFailedMessages = new AtomicInteger(0);
        int totalMessageCount = messages.size();
        int progressIndex = 0;
        while (!messages.isEmpty()) {
            // While the number of messages in flight is at the maximum, wait for messages to return.
            while (numberOfInFlightRequests.get() >= maximumInFlightRequests) {
                ThreadUtil.sleep(100L);
            }

            // Display progress every 5%.
            int progressThreshold = (progressIndex + 1) * totalMessageCount / 20;
            int progressCount = totalMessageCount - messages.size();
            if (progressCount >= progressThreshold) {
                // Calculate the percentage and
                double percentage = progressCount * 100.0 / totalMessageCount;
                output.println(String.format("%d/%d sent (%.1f%%)", progressCount, totalMessageCount, percentage));

                // Update the progress index.
                progressIndex = (progressCount * 20 / totalMessageCount) + 1;
            }

            // Find a message that needs to be sent.
            PendingMessage messageToSend = null;
            Iterator<PendingMessage> iterator = messages.iterator();
            while (iterator.hasNext() && messageToSend == null) {
                PendingMessage message = iterator.next();
                if (message.getNumberOfSuccesses() == 0 &&
                        message.getNumberOfFailures() == message.getNumberOfAttempts() &&
                        message.getNumberOfAttempts() < maximumMessageAttempts) {
                    messageToSend = message;
                }
            }

            // If no message was found, sleep for a short time to allow states to update. Otherwise, send the message.
            if (messageToSend == null) {
                ThreadUtil.sleep(100L);
            } else {
                // Store a reference that can be accessed from the callback. Increment the number of attempts.
                final PendingMessage messageToSendFinal = messageToSend;
                messageToSendFinal.incrementAndGetNumberOfAttempts();

                // Build the message. If no signer seed is provided, sign with the default verifier seed.
                Message message;
                if (messageToSend.getSignerSeed() == null) {
                    message = new Message(messageToSendFinal.getMessageType(), messageToSendFinal.getMessageObject());
                } else {
                    message = new Message(messageToSendFinal.getMessageType(), messageToSendFinal.getMessageObject(),
                            messageToSend.getSignerSeed());
                }

                // Send the message. Increment the appropriate counters in the callback.
                Message.fetch(messageToSendFinal.getRecipient(), message, new MessageCallback() {
                    @Override
                    public void responseReceived(Message message) {
                        if (message == null) {
                            messageToSendFinal.incrementAndGetNumberOfFailures();
                            numberOfFailedMessages.incrementAndGet();
                        } else {
                            messageToSendFinal.incrementAndGetNumberOfSuccesses();
                            numberOfSuccessfulMessages.incrementAndGet();
                        }
                    }
                });
            }

            // Clean the map.
            Set<PendingMessage> pendingMessagesCopy = new HashSet<>(messages);
            for (PendingMessage message : pendingMessagesCopy) {
                if (message.getNumberOfAttempts() >= maximumMessageAttempts ||
                        message.getNumberOfSuccesses() > 0) {
                    messages.remove(message);
                }
            }
        }

        LogUtil.println("number of successful messages: " + numberOfSuccessfulMessages.get());
        LogUtil.println("number of failed messages: " + numberOfFailedMessages.get());
    }
}
