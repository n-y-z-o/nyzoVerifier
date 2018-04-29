package co.nyzo.verifier.tests;

import co.nyzo.verifier.*;

import java.util.ArrayList;
import java.util.List;

public class BlockSerializeDeserializeTest {

    public static void main(String[] args) {

        long height = 0L;
        byte[] previousBlockHash = new byte[FieldByteSize.hash];
        long startTimestamp = 0L;
        List<Transaction> transactions = new ArrayList<>();
        byte rolloverFees = 4;
        List<byte[]> previousVerifiers = new ArrayList<>();
        List<BalanceListItem> items = new ArrayList<>();
        BalanceList balanceList = new BalanceList(height, rolloverFees, previousVerifiers, items);
        Block block = new Block(height, previousBlockHash, startTimestamp, transactions, balanceList.getHash(),
                balanceList);

        byte[] serialized = block.getBytes();
        Block fromBytes = Block.fromBytes(serialized);

        System.out.println("block from bytes is " + fromBytes);
        System.out.println("balance list hash (" + ByteUtil.arrayAsStringWithDashes(block.getBalanceListHash()) +
                "): " + (ByteUtil.arraysAreEqual(block.getBalanceListHash(), fromBytes.getBalanceListHash()) ? "PASS" :
                        "FAIL"));
        System.out.println("block hash (" + ByteUtil.arrayAsStringWithDashes(block.getHash()) + "): " +
                (ByteUtil.arraysAreEqual(block.getHash(), fromBytes.getHash()) ? "PASS" : "FAIL"));

    }
}
