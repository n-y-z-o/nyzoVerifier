package co.nyzo.verifier.scripts;

import co.nyzo.verifier.MeshListener;
import co.nyzo.verifier.Message;
import co.nyzo.verifier.MessageCallback;
import co.nyzo.verifier.MessageType;
import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.messages.debug.BlockDelayResponse;
import co.nyzo.verifier.nyzoString.NyzoString;
import co.nyzo.verifier.nyzoString.NyzoStringEncoder;
import co.nyzo.verifier.nyzoString.NyzoStringPrivateSeed;
import co.nyzo.verifier.util.LogUtil;
import co.nyzo.verifier.util.ThreadUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.util.concurrent.atomic.AtomicBoolean;

public class BlockDelayRequestScript {

    public static void main(String[] args) {
        // Check the length of the argument array. Return if insufficient arguments are provided. To simplify the design
        // of this script, both host name/IP and verifier seed are required.
        if (args.length < 2) {
            LogUtil.println("\n\n\n");
            LogUtil.println("***********************************************************************");
            LogUtil.println("arguments:");
            LogUtil.println("- host name or IP address of your verifier");
            LogUtil.println("- Nyzo string private key of your verifier");
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

        // Send the request.
        AtomicBoolean receivedResponse = new AtomicBoolean(false);
        byte[] privateSeed = ((NyzoStringPrivateSeed) privateSeedObject).getSeed();
        Message message = new Message(MessageType.BlockDelayRequest422, null, privateSeed);
        Message.fetchTcp(hostNameOrIp, MeshListener.standardPortTcp, message, new MessageCallback() {
            @Override
            public void responseReceived(Message message) {
                if (message == null) {
                    LogUtil.println(ConsoleColor.Red.backgroundBright() + "response is null" + ConsoleColor.reset);
                } else if (!(message.getContent() instanceof BlockDelayResponse)) {
                    LogUtil.println(ConsoleColor.Red.backgroundBright() + "response is incorrect type: " + message +
                            ConsoleColor.reset);
                } else {
                    BlockDelayResponse response = (BlockDelayResponse) message.getContent();
                    ConsoleColor color = response.isSuccess() ? ConsoleColor.Green : ConsoleColor.Red;
                    LogUtil.println(color.backgroundBright() + "response: " + response + ConsoleColor.reset);
                }

                receivedResponse.set(true);
            }
        });

        // Wait for the response.
        while (!receivedResponse.get()) {
            ThreadUtil.sleep(500L);
        }

        UpdateUtil.terminate();
    }
}
