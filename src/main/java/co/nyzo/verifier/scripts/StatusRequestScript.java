package co.nyzo.verifier.scripts;

import co.nyzo.verifier.*;
import co.nyzo.verifier.messages.StatusResponse;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class StatusRequestScript {

    public static void main(String[] args) {

        if (args.length != 1) {
            System.out.println("\n\n\n*****************************************************************");
            System.out.println("required argument:");
            System.out.println("- IP address of your verifier");
            System.out.println("  OR");
            System.out.println("- private seed of your verifier");
            System.out.println("*****************************************************************\n\n\n");
            return;
        }

        // The IP address is a v4 IP, so the easiest way to distinguish between IP and private seed is argument length.
        // The maximum string length of a v4 IP is 15 (xxx.xxx.xxx.xxx), while a seed is considerably longer.
        List<byte[]> ipAddresses;
        byte[] privateSeed;
        if (args[0].length() > 15) {

            // Get the private seed and corresponding identifier that was provided as the argument.
            privateSeed = ByteUtil.byteArrayFromHexString(args[0], FieldByteSize.seed);
            byte[] verifierIdentifier = KeyUtil.identifierForSeed(privateSeed);

            // Get the IP addresses of the verifier.
            ipAddresses = ScriptUtil.ipAddressesForVerifier(verifierIdentifier);
            if (ipAddresses.isEmpty()) {
                System.out.println("unable to find IP address of " +
                        ByteUtil.arrayAsStringWithDashes(verifierIdentifier));
            }
        } else {

            ipAddresses = new ArrayList<>();
            privateSeed = null;

            // Get the IP address of the verifier from the argument.
            byte[] ipAddress = IpUtil.addressFromString(args[0]);
            if (ByteUtil.isAllZeros(ipAddress)) {
                System.out.println("please provide a valid IP address");
            } else {
                ipAddresses.add(ipAddress);
            }
        }

        // Send the request to our verifier instances.
        AtomicInteger numberOfResponsesNotYetReceived = new AtomicInteger(ipAddresses.size());
        Message message = new Message(MessageType.StatusRequest17, null);
        if (privateSeed != null) {
            message.sign(privateSeed);
        }
        for (byte[] ipAddress : ipAddresses) {
            Message.fetchTcp(IpUtil.addressAsString(ipAddress), MeshListener.standardPortTcp, message,
                    new MessageCallback() {
                        @Override
                        public void responseReceived(Message message) {

                            if (message == null) {
                                System.out.println("response message is null");
                            } else {

                                // Get the response object from the message.
                                StatusResponse response = (StatusResponse) message.getContent();

                                // Print the response.
                                for (String line : response.getLines()) {
                                    System.out.println(line);
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
}
