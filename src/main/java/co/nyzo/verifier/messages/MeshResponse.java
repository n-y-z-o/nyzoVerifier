package co.nyzo.verifier.messages;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.MessageObject;
import co.nyzo.verifier.Node;
import co.nyzo.verifier.Transaction;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MeshResponse implements MessageObject {

    private List<Node> mesh;

    public MeshResponse(List<Node> mesh) {
        this.mesh = mesh;
    }

    public List<Node> getMesh() {
        return mesh;
    }

    @Override
    public int getByteSize() {

        return FieldByteSize.nodeListLength + mesh.size() * (Node.getByteSizeStatic());
    }

    @Override
    public byte[] getBytes() {

        byte[] result = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(result);
        buffer.putInt(mesh.size());
        for (Node node : mesh) {
            buffer.put(node.getBytes());
        }

        return result;
    }

    public static TransactionPoolResponse fromByteBuffer(ByteBuffer buffer) {

        TransactionPoolResponse result = null;

        try {
            List<Transaction> transactions = new ArrayList<>();

            int numberOfTransactions = buffer.getInt();
            for (int i = 0; i < numberOfTransactions; i++) {
                transactions.add(Transaction.fromByteBuffer(buffer));
            }

            result = new TransactionPoolResponse(transactions);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("[MeshResponse(" + mesh.size() + "): {");
        String separator = "";
        for (int i = 0; i < mesh.size() && i < 5; i++) {
            result.append(separator).append(mesh.get(i).toString());
            separator = ",";
        }
        if (mesh.size() > 5) {
            result.append("...");
        }
        result.append("}]");

        return result.toString();
    }
}
