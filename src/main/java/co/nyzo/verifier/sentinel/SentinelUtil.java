package co.nyzo.verifier.sentinel;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.messages.BlockRequest;
import co.nyzo.verifier.messages.BlockResponse;
import co.nyzo.verifier.messages.BootstrapRequest;
import co.nyzo.verifier.messages.BootstrapResponseV2;
import co.nyzo.verifier.util.LogUtil;
import co.nyzo.verifier.util.ThreadUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SentinelUtil {

    public static void initializeFrozenEdge(List<ManagedVerifier> verifiers) {

        boolean completedInitialization = false;
        while (!completedInitialization) {
            Set<BootstrapResponseV2> bootstrapResponses = fetchBootstrapResponses(verifiers);
            LogUtil.println("forzen edge initialization: got " + bootstrapResponses.size() + " bootstrap responses");
            completedInitialization = processBootstrapResponses(bootstrapResponses, verifiers);

            // If initialization was not completed, sleep for 5 seconds to prevent looping too tightly and hammering
            // the managed verifiers.
            if (!completedInitialization) {
                LogUtil.println(ConsoleColor.Yellow.background() + "frozen edge initialization failed; retrying" +
                        ConsoleColor.reset);
                ThreadUtil.sleep(5000L);
            }
        }
    }

    private static Set<BootstrapResponseV2> fetchBootstrapResponses(List<ManagedVerifier> verifiers) {

        // Try to get a bootstrap response from each managed node. While we fully trust every node we manage, some may
        // be behind, so we want to query as many as possible to ensure we start as close to the cycle frozen edge as
        // possible. Continue looping until at least one response is received.
        Set<BootstrapResponseV2> bootstrapResponses = ConcurrentHashMap.newKeySet();
        while (bootstrapResponses.isEmpty()) {

            AtomicInteger numberOfResponsesPending = new AtomicInteger(verifiers.size());
            for (ManagedVerifier verifier : verifiers) {

                Message bootstrapRequest = new Message(MessageType.BootstrapRequestV2_35, new BootstrapRequest());
                Message.fetchTcp(verifier.getHost(), verifier.getPort(), bootstrapRequest, new MessageCallback() {
                    @Override
                    public void responseReceived(Message message) {

                        System.out.println("response from " + verifier.getHost() + " is " + message);
                        if (message != null && (message.getContent() instanceof BootstrapResponseV2)) {
                            bootstrapResponses.add((BootstrapResponseV2) message.getContent());
                        }
                        numberOfResponsesPending.decrementAndGet();
                    }
                });
            }

            // Wait for all responses to return.
            int waitIteration = 0;
            while (numberOfResponsesPending.get() > 0) {
                ThreadUtil.sleep(300L);
                System.out.println("after wait iteration " + waitIteration++ + ", " + numberOfResponsesPending.get() +
                        " bootstrap responses pending, have " + bootstrapResponses.size() + " good responses");
            }
        }

        return bootstrapResponses;
    }

    private static boolean processBootstrapResponses(Set<BootstrapResponseV2> bootstrapResponses,
                                                     List<ManagedVerifier> verifiers) {

        boolean successful = false;

        System.out.println("processing bootstrap responses");

        // Get the local and chain frozen edges. If the chain is within four cycles of the local frozen edge, we do
        // not need to fetch anything here. The standard block-fetch process will get the chain. If the chain is not
        // within four cycles of the local frozen edge, we fetch and freeze one recent block.
        long localFrozenEdge = BlockManager.getFrozenEdgeHeight();
        long chainFrozenEdge = localFrozenEdge;
        List<ByteBuffer> cycleVerifiers = new ArrayList<>();
        for (BootstrapResponseV2 bootstrapResponse : bootstrapResponses) {
            if (bootstrapResponse.getFrozenEdgeHeight() > chainFrozenEdge) {
                chainFrozenEdge = bootstrapResponse.getFrozenEdgeHeight();
                cycleVerifiers = bootstrapResponse.getCycleVerifiers();
            }
        }

        long cutoffHeight = chainFrozenEdge - cycleVerifiers.size() * 4;
        if (localFrozenEdge >= cutoffHeight) {
            // If the frozen edge is close enough, nothing needs to be done. Flag processing as successful.
            successful = true;
        } else {

            // Try to get the block at the chain frozen edge. This sends requests to every managed verifier, but only
            // one good response is needed.
            Set<Block> blocks = ConcurrentHashMap.newKeySet();
            Set<BalanceList> balanceLists = ConcurrentHashMap.newKeySet();
            long requestHeight = chainFrozenEdge;
            Message message = new Message(MessageType.BlockRequest11, new BlockRequest(requestHeight, requestHeight,
                    true));
            AtomicInteger numberOfResponsesPending = new AtomicInteger(verifiers.size());
            for (ManagedVerifier verifier : verifiers) {

                Message.fetchTcp(verifier.getHost(), verifier.getPort(), message, new MessageCallback() {
                    @Override
                    public void responseReceived(Message message) {

                        if (message != null) {
                            try {
                                BlockResponse blockResponse = (BlockResponse) message.getContent();
                                Block block = blockResponse.getBlocks().get(0);
                                BalanceList balanceList = blockResponse.getInitialBalanceList();
                                if (block.getBlockHeight() == requestHeight &&
                                        balanceList.getBlockHeight() == requestHeight) {
                                    blocks.add(block);
                                    balanceLists.add(balanceList);
                                }
                            } catch (Exception ignored) { }
                        }

                        numberOfResponsesPending.decrementAndGet();
                    }
                });
            }

            // Wait for all responses to return.
            int waitIteration = 0;
            while (numberOfResponsesPending.get() > 0) {
                ThreadUtil.sleep(300L);
                System.out.println("after wait iteration " + waitIteration++ + ", " + numberOfResponsesPending.get() +
                        " block responses pending");
            }

            // If a block was obtained, freeze it and flag that we have successfully processed the bootstrap response.
            if (!blocks.isEmpty() && !balanceLists.isEmpty()) {

                Block block = blocks.iterator().next();
                BalanceList balanceList = balanceLists.iterator().next();
                BlockManager.freezeBlock(block, block.getPreviousBlockHash(), balanceList, cycleVerifiers);

                successful = true;
            }
        }

        return successful;
    }
}
