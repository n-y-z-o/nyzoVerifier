package co.nyzo.verifier.tests;

import co.nyzo.verifier.*;
import co.nyzo.verifier.util.UpdateUtil;

import java.util.ArrayList;
import java.util.List;

public class BlockSendTest {

    private static final String[] nodes = { "verifier1.nyzo.co" };

    public static void main(String[] args) throws Exception {

        Block genesisBlock = BlockManager.frozenBlockForHeight(0L);

        List<Transaction> transactions = new ArrayList<>();
        BalanceList balanceList = Block.balanceListForNextBlock(genesisBlock, transactions, Verifier.getIdentifier());
        long startTimestamp = genesisBlock.getStartTimestamp() + Block.blockDuration;
        Block block = new Block(1L, genesisBlock.getHash(), startTimestamp, transactions,
                HashUtil.doubleSHA256(balanceList.getBytes()), balanceList);
        System.out.println("block is " + block);

        System.out.println("block cycle information is " + block.getCycleInformation());

        Message message = new Message(MessageType.NewBlock9, block);
        for (String node : nodes) {
            Message.fetch(node, 9444, message, false, new MessageCallback() {
                @Override
                public void responseReceived(Message message) {
                    System.out.println("BlockSendTest: response is " + message);
                }
            });
        }

        Thread.sleep(1000L);
        UpdateUtil.terminate();
    }
}
