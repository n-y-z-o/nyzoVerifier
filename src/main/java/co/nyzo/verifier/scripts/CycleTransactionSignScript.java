package co.nyzo.verifier.scripts;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.ClientTransactionUtil;
import co.nyzo.verifier.client.CommandOutputConsole;
import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.messages.BootstrapRequest;
import co.nyzo.verifier.messages.BootstrapResponseV2;
import co.nyzo.verifier.messages.MeshResponse;
import co.nyzo.verifier.nyzoString.NyzoString;
import co.nyzo.verifier.nyzoString.NyzoStringEncoder;
import co.nyzo.verifier.nyzoString.NyzoStringSignature;
import co.nyzo.verifier.sentinel.ManagedVerifier;
import co.nyzo.verifier.sentinel.Sentinel;
import co.nyzo.verifier.sentinel.SentinelUtil;
import co.nyzo.verifier.util.*;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class CycleTransactionSignScript {

    public static void main(String[] args) {
        signTransactions(args);
        UpdateUtil.terminate();
    }

    private static void signTransactions(String[] args) {

        // Check the length of the argument array. Return if insufficient arguments are provided.
        if (args.length < 2) {
            LogUtil.println("\n\n\n");
            LogUtil.println("***********************************************************************");
            LogUtil.println("arguments:");
            LogUtil.println("- Nyzo string signature of the cycle transaction you want to sign");
            LogUtil.println("- 1 to vote for the transaction, 0 to vote against the transaction");
            LogUtil.println("***********************************************************************\n\n\n");
            return;
        }

        // Get the signature. Return if it is not valid.
        NyzoString decodedSignature = NyzoStringEncoder.decode(args[0]);
        if (!(decodedSignature instanceof NyzoStringSignature)) {
            LogUtil.println(ConsoleColor.Red.backgroundBright() + "not a valid Nyzo string signature: " + args[0] +
                    ConsoleColor.reset);
            return;
        }

        // Get the vote. Return if it is not valid.
        String voteString = args[1];
        if (!voteString.equals("1") && !voteString.equals("0")) {
            LogUtil.println(ConsoleColor.Red.backgroundBright() + "vote is invalid, must be 1 or 0: " + args[0] +
                    ConsoleColor.reset);
            return;
        }
        byte vote = voteString.equals("1") ? (byte) 1 : (byte) 0;

        // Get the managed verifiers. Return if none are set.
        List<ManagedVerifier> managedVerifiers = Sentinel.getManagedVerifiers();
        if (managedVerifiers.isEmpty()) {
            LogUtil.println(ConsoleColor.Red.backgroundBright() + "no managed verifiers specified in " +
                    Sentinel.managedVerifiersFile + ConsoleColor.reset);
            return;
        }

        // Initialize the frozen edge. This is necessary for proper operation of CycleTransactionManager.
        SentinelUtil.initializeFrozenEdge(managedVerifiers);

        // Get the transaction. Return if unable to find it.
        Transaction transaction = getTransaction(((NyzoStringSignature) decodedSignature).getSignature());
        if (transaction == null) {
            LogUtil.println(ConsoleColor.Red.backgroundBright() + "unable to find specified transaction" +
                    ConsoleColor.reset);
            return;
        }

        // Fetch the cycle nodes. Return if empty.
        Set<Node> cycleNodes = fetchCycleNodes(managedVerifiers);
        if (cycleNodes.isEmpty()) {
            LogUtil.println(ConsoleColor.Red.backgroundBright() + "unable to fetch cycle nodes" + ConsoleColor.reset);
            return;
        }

        // Fetch the cycle order.
        List<ByteBuffer> cycleOrder = fetchCycleOrder(managedVerifiers);
        if (cycleOrder.isEmpty()) {
            LogUtil.println(ConsoleColor.Red.backgroundBright() + "unable to fetch cycle order" + ConsoleColor.reset);
            return;
        }

        // Send the signatures.
        sendSignatures(transaction.getSignature(), vote, cycleNodes, cycleOrder, managedVerifiers);
    }

    private static Transaction getTransaction(byte[] signature) {

        // Try to get the transaction from the frozen-edge balance list.
        BalanceList balanceList = BalanceListManager.getFrozenEdgeList();
        Transaction result = null;
        for (Transaction transaction : balanceList.getPendingCycleTransactions().values()) {
            if (ByteUtil.arraysAreEqual(signature, transaction.getSignature())) {
                result = transaction;
            }
        }

        return result;
    }

    private static Set<Node> fetchCycleNodes(List<ManagedVerifier> managedVerifiers) {

        Set<Node> cycleNodes = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < managedVerifiers.size() && cycleNodes.isEmpty(); i++) {
            Message meshRequest = new Message(MessageType.MeshRequest15, null);
            ManagedVerifier verifier = managedVerifiers.get(i);
            AtomicBoolean processedResponse = new AtomicBoolean(false);
            Message.fetchTcp(verifier.getHost(), verifier.getPort(), meshRequest, new MessageCallback() {
                @Override
                public void responseReceived(Message message) {
                    if (message != null && (message.getContent() instanceof MeshResponse)) {
                        MeshResponse response = (MeshResponse) message.getContent();
                        cycleNodes.addAll(response.getMesh());
                    }
                    processedResponse.set(true);
                }
            });

            // Wait up to 2 seconds for the response to be processed.
            for (int j = 0; j < 10 && !processedResponse.get(); j++) {
                ThreadUtil.sleep(200L);
            }
        }

        return cycleNodes;
    }

    private static List<ByteBuffer> fetchCycleOrder(List<ManagedVerifier> managedVerifiers) {

        List<ByteBuffer> cycleOrder = new CopyOnWriteArrayList<>();
        for (int i = 0; i < managedVerifiers.size() && cycleOrder.isEmpty(); i++) {
            Message meshRequest = new Message(MessageType.BootstrapRequestV2_35, new BootstrapRequest());
            ManagedVerifier verifier = managedVerifiers.get(i);
            AtomicBoolean processedResponse = new AtomicBoolean(false);
            Message.fetchTcp(verifier.getHost(), verifier.getPort(), meshRequest, new MessageCallback() {
                @Override
                public void responseReceived(Message message) {
                    if (message != null && (message.getContent() instanceof BootstrapResponseV2)) {
                        BootstrapResponseV2 response = (BootstrapResponseV2) message.getContent();
                        cycleOrder.addAll(response.getCycleVerifiers());
                    }
                    processedResponse.set(true);
                }
            });

            // Wait up to 2 seconds for the response to be processed.
            for (int j = 0; j < 10 && !processedResponse.get(); j++) {
                ThreadUtil.sleep(200L);
            }
        }

        return cycleOrder;
    }

    private static void sendSignatures(byte[] transactionSignature, byte vote, Set<Node> cycleNodes,
                                       List<ByteBuffer> cycleOrder, List<ManagedVerifier> managedVerifiers) {

        // For each managed verifier, create the signature transaction and message.
        CommandOutputConsole output = new CommandOutputConsole();
        Set<PendingMessage> messages = ConcurrentHashMap.newKeySet();
        for (ManagedVerifier verifier : managedVerifiers) {
            // Make the transaction.
            long timestamp = ClientTransactionUtil.suggestedTransactionTimestamp();
            Transaction transaction = Transaction.cycleSignatureTransaction(timestamp, vote, transactionSignature,
                    verifier.getSeed());

            // Make a message to send the transaction to the next 5 verifiers in the cycle.
            int numberOfVerifiers = Math.min(5, cycleOrder.size());
            for (int i = 0; i < numberOfVerifiers; i++) {
                byte[] identifier = cycleOrder.get(i).array();
                for (Node node : cycleNodes) {
                    if (ByteUtil.arraysAreEqual(identifier, node.getIdentifier())) {
                        messages.add(new PendingMessage(node, MessageType.Transaction5, transaction,
                                verifier.getSeed()));
                    }
                }
            }

        }

        // Send the messages.
        ScriptUtil.sendMessages(messages, new CommandOutputConsole());
    }
}
