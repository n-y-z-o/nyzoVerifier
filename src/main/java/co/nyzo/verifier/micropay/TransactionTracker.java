package co.nyzo.verifier.micropay;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.ThreadUtil;
import co.nyzo.verifier.util.UpdateUtil;

public class TransactionTracker {

    // This class is intended to, eventually, provide a comprehensive, persistent tracking of transactions received by
    // this server. For this initial version, it only prints transactions sent to this server's verifier identifier as
    // blocks are received.

    public static void start() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
                while (!UpdateUtil.shouldTerminate()) {
                    // If a new block is available, print the transactions.
                    Block frozenEdge = BlockManager.getFrozenEdge();
                    if (frozenEdge.getBlockHeight() > frozenEdgeHeight) {
                        byte[] verifierIdentifier = Verifier.getIdentifier();
                        for (Transaction transaction : frozenEdge.getTransactions()) {
                            if (ByteUtil.arraysAreEqual(verifierIdentifier, transaction.getReceiverIdentifier())) {
                                System.out.println(ConsoleColor.Green + "received " +
                                        PrintUtil.printAmount(transaction.getAmount()) + " in block " +
                                        frozenEdge.getBlockHeight() + ConsoleColor.reset);
                            }
                        }

                        // Advance the local frozen edge height.
                        frozenEdgeHeight = frozenEdge.getBlockHeight();
                    }

                    // Sleep to avoid consuming too much CPU. This thread is not time-sensitive, so any value less than
                    // the typical block interval is acceptable.
                    ThreadUtil.sleep(4000L);
                }
            }
        }).start();
    }
}
