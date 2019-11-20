package co.nyzo.verifier.scripts;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.messages.CycleTransactionSignature;
import co.nyzo.verifier.messages.MeshResponse;
import co.nyzo.verifier.messages.TransactionListResponse;
import co.nyzo.verifier.nyzoString.NyzoString;
import co.nyzo.verifier.nyzoString.NyzoStringEncoder;
import co.nyzo.verifier.nyzoString.NyzoStringSignature;
import co.nyzo.verifier.sentinel.ManagedVerifier;
import co.nyzo.verifier.sentinel.Sentinel;
import co.nyzo.verifier.sentinel.SentinelUtil;
import co.nyzo.verifier.util.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class CycleTransactionSignScript {

    public static void main(String[] args) {
        signTransactions(args);
        UpdateUtil.terminate();
    }

    private static void signTransactions(String[] args) {

        // Check the length of the argument array. Return if insufficient arguments are provided.
        if (args.length < 1) {
            LogUtil.println("\n\n\n***********************************************************************");
            LogUtil.println("arguments:");
            LogUtil.println("- Nyzo string signature of the cycle transaction you want to sign");
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
        Transaction transaction = getTransaction(((NyzoStringSignature) decodedSignature).getSignature(),
                managedVerifiers);
        if (transaction == null) {
            LogUtil.println(ConsoleColor.Red.backgroundBright() + "unable to find specified transaction" +
                    ConsoleColor.reset);
            return;
        }

        // Fetch the cycle. Return if empty.
        Set<Node> cycle = fetchCycle(managedVerifiers);
        if (cycle.isEmpty()) {
            LogUtil.println(ConsoleColor.Red.backgroundBright() + "unable to fetch cycle" + ConsoleColor.reset);
            return;
        }

        // Send the signatures.
        sendSignatures(transaction, cycle, managedVerifiers);
    }

    private static Transaction getTransaction(byte[] signature, List<ManagedVerifier> managedVerifiers) {

        // Try to get the transaction from the cycle transaction manager. The loop queries all of the managed verifiers,
        // if necessary, to find the transaction.
        Transaction transaction = null;
        for (int i = 0; i < managedVerifiers.size() + 1; i++) {
            Collection<Transaction> localTransactions = CycleTransactionManager.getTransactions();
            for (Transaction localTransaction : localTransactions) {
                if (ByteUtil.arraysAreEqual(signature, localTransaction.getSignature())) {
                    transaction = localTransaction;
                }
            }

            // If the transaction is null, and this iteration is not past the end of the managed verifiers list, query
            // the managed verifier to try to get the transaction.
            if (transaction == null && i < managedVerifiers.size()) {
                ManagedVerifier verifier = managedVerifiers.get(i);
                LogUtil.println(ConsoleColor.Yellow.background() + "sending CycleTransactionListRequest to " +
                        verifier.getHost() + ConsoleColor.reset);
                Message message = new Message(MessageType.CycleTransactionListRequest49, null);
                message.sign(verifier.getSeed());
                AtomicBoolean processedResponse = new AtomicBoolean(false);
                Message.fetchTcp(verifier.getHost(), MeshListener.standardPortTcp,
                        message, new MessageCallback() {
                            @Override
                            public void responseReceived(Message message) {
                                processTransactionListResponse(message);
                                processedResponse.set(true);
                            }
                        });

                // Wait up to 2 seconds for the response to be processed.
                for (int j = 0; j < 10 && !processedResponse.get(); j++) {
                    ThreadUtil.sleep(200L);
                }

                // Perform maintenance to persist the transaction if one was found.
                CycleTransactionManager.performMaintenance();
            }
        }

        return transaction;
    }

    private static void processTransactionListResponse(Message message) {

        if (message != null && (message.getContent() instanceof TransactionListResponse)) {
            TransactionListResponse response = (TransactionListResponse) message.getContent();
            for (Transaction transaction : response.getTransactions()) {
                CycleTransactionManager.registerTransaction(transaction, null, null);
            }
        }
    }

    private static Set<Node> fetchCycle(List<ManagedVerifier> managedVerifiers) {

        Set<Node> cycle = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < managedVerifiers.size() && cycle.isEmpty(); i++) {
            Message meshRequest = new Message(MessageType.MeshRequest15, null);
            ManagedVerifier verifier = managedVerifiers.get(i);
            AtomicBoolean processedResponse = new AtomicBoolean(false);
            Message.fetchTcp(verifier.getHost(), verifier.getPort(), meshRequest, new MessageCallback() {
                @Override
                public void responseReceived(Message message) {
                    if (message != null && (message.getContent() instanceof MeshResponse)) {
                        MeshResponse response = (MeshResponse) message.getContent();
                        cycle.addAll(response.getMesh());
                    }
                    processedResponse.set(true);
                }
            });

            // Wait up to 2 seconds for the response to be processed.
            for (int j = 0; j < 10 && !processedResponse.get(); j++) {
                ThreadUtil.sleep(200L);
            }
        }

        return cycle;
    }

    private static void sendSignatures(Transaction transaction, Set<Node> cycle,
                                       List<ManagedVerifier> managedVerifiers) {

        // Build the set of messages.
        Set<PendingMessage> messages = ConcurrentHashMap.newKeySet();
        for (ManagedVerifier verifier : managedVerifiers) {

            // Create the signature for this verifier.
            CycleTransactionSignature signature = new CycleTransactionSignature(transaction.getSenderIdentifier(),
                    verifier.getIdentifier(), SignatureUtil.signBytes(transaction.getBytes(true), verifier.getSeed()));

            // Add a message for every verifier.
            for (Node node : cycle) {
                messages.add(new PendingMessage(node, MessageType.CycleTransactionSignature47, signature,
                        verifier.getSeed()));
            }
        }

        // Send the messages.
        ScriptUtil.sendMessages(messages);
    }
}
