package co.nyzo.verifier.scripts;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.messages.IpAddressMessageObject;
import co.nyzo.verifier.messages.WhitelistResponse;
import co.nyzo.verifier.nyzoString.NyzoString;
import co.nyzo.verifier.nyzoString.NyzoStringEncoder;
import co.nyzo.verifier.nyzoString.NyzoStringPrivateSeed;
import co.nyzo.verifier.util.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MeshListenerStressScript {

    private static final int defaultNumberOfRequests = 100;

    public static void main(String[] args) {


        // Check the length of the argument array. Return if insufficient arguments are provided. To simplify the design
        // of this script, both host name/IP and verifier seed are required.
        if (args.length < 2) {
            LogUtil.println("\n\n\n");
            LogUtil.println("***********************************************************************");
            LogUtil.println("arguments:");
            LogUtil.println("- host name or IP address of your verifier");
            LogUtil.println("- Nyzo string private key of your verifier");
            LogUtil.println("- number of requests (optional; default " + defaultNumberOfRequests + ")");
            LogUtil.println("***********************************************************************\n\n\n");
            return;
        }

        // Get the host name/IP address.
        String hostNameOrIp = args[0];

        // Get the private key. Return if invalid.
        NyzoString privateSeedObject = NyzoStringEncoder.decode(args[1]);
        if (!(privateSeedObject instanceof NyzoStringPrivateSeed)) {
            LogUtil.println(ConsoleColor.Red.backgroundBright() + args[1] + " is not a valid Nyzo string private seed" +
                    ConsoleColor.reset);
            return;
        }

        // Get the number of requests.
        int numberOfRequests = defaultNumberOfRequests;
        if (args.length >= 3) {
            try {
                numberOfRequests = Integer.parseInt(args[2]);
            } catch (Exception ignored) { }

            if (numberOfRequests < 1) {
                numberOfRequests = defaultNumberOfRequests;
            }
        }

        // Send the whitelist request. This allows us to send a large number of messages without triggering the
        // blacklist.
        byte[] privateSeed = ((NyzoStringPrivateSeed) privateSeedObject).getSeed();
        int port = MeshListener.standardPortTcp;
        boolean whitelistingSuccessful = sendWhitelistRequest(hostNameOrIp, port, privateSeed);
        LogUtil.println("whitelisting successful: " + whitelistingSuccessful);

        // Send the requests.
        if (whitelistingSuccessful) {
            AtomicInteger activeThreads = new AtomicInteger(numberOfRequests);
            for (int i = 0; i < numberOfRequests; i++) {
                int index = i;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Message message = new Message(MessageType.FrozenEdgeBalanceListRequest45, null);
                        Message.fetchTcp(hostNameOrIp, port, message, new MessageCallback() {
                            @Override
                            public void responseReceived(Message message) {
                                activeThreads.decrementAndGet();
                            }
                        });
                    }
                }).start();
            }

            // Wait for all threads to complete.
            while (activeThreads.get() > 0) {
                ThreadUtil.sleep(300L);
                LogUtil.println("waiting for " + activeThreads.get() + " threads to complete");
            }
        }

        UpdateUtil.terminate();
    }

    private static boolean sendWhitelistRequest(String host, int port, byte[] seed) {

        AtomicBoolean complete = new AtomicBoolean(false);
        AtomicBoolean successful = new AtomicBoolean(false);

        // Get the IP address of this sentinel according to the managed verifier.
        Message ipRequest = new Message(MessageType.IpAddressRequest53, null, seed);
        Message.fetchTcp(host, port, ipRequest, new MessageCallback() {
            @Override
            public void responseReceived(Message message) {
                // If the response identifier is correct and the content type is correct, send the whitelist request.
                byte[] verifierIdentifier = KeyUtil.identifierForSeed(seed);
                if (ByteUtil.arraysAreEqual(message.getSourceNodeIdentifier(), verifierIdentifier) &&
                        (message.getContent() instanceof IpAddressMessageObject)) {
                    IpAddressMessageObject ipAddress = (IpAddressMessageObject) message.getContent();
                    Message whitelistRequest = new Message(MessageType.WhitelistRequest424, ipAddress, seed);
                    Message.fetchTcp(host, port, whitelistRequest, new MessageCallback() {
                        @Override
                        public void responseReceived(Message message) {
                            LogUtil.println("whitelist response from " + host + ": " + message);
                            complete.set(true);
                            if (message.getContent() instanceof WhitelistResponse) {
                                WhitelistResponse response = (WhitelistResponse) message.getContent();
                                successful.set(response.isSuccess());
                            }
                        }
                    });
                } else {
                    LogUtil.println("timestamp request for whitelisting failed");
                    if (!ByteUtil.arraysAreEqual(message.getSourceNodeIdentifier(), verifierIdentifier)) {
                        LogUtil.println("verifier identifier incorrect; expected=" +
                                PrintUtil.compactPrintByteArray(verifierIdentifier) + ", actual=" +
                                PrintUtil.compactPrintByteArray(message.getSourceNodeIdentifier()));
                    }
                    complete.set(true);
                }
            }
        });

        while (!complete.get()) {
            ThreadUtil.sleep(300L);
        }

        return successful.get();
    }
}
