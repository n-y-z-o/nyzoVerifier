package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class NodeJoinResponse implements MessageObject, PortMessage {

    private static final int maximumVotes = 100;

    private String nickname;
    private int port;
    private List<BlockVote> blockVotes;
    private NewVerifierVote newVerifierVote;

    public NodeJoinResponse() {

        this.nickname = Verifier.getNickname();
        this.port = MeshListener.getPort();
        this.blockVotes = BlockVoteManager.getLocalVotes();
        limitBlockVoteListSize();
        this.newVerifierVote = NewVerifierVoteManager.getLocalVote();
    }

    public NodeJoinResponse(String nickname, int port, List<BlockVote> blockVotes, NewVerifierVote newVerifierNote) {

        this.nickname = nickname == null ? "" : nickname;
        this.port = port;
        this.blockVotes = blockVotes;
        limitBlockVoteListSize();
        this.newVerifierVote = newVerifierNote;
    }

    private void limitBlockVoteListSize() {

        while (blockVotes.size() > maximumVotes) {
            blockVotes.remove(blockVotes.size() - 1);
        }
    }

    public String getNickname() {

        return nickname;
    }

    public int getPort() {
        return port;
    }

    public List<BlockVote> getBlockVotes() {

        return blockVotes;
    }

    public NewVerifierVote getNewVerifierVote() {
        return newVerifierVote;
    }

    @Override
    public int getByteSize() {

        return FieldByteSize.string(nickname) + FieldByteSize.port + FieldByteSize.voteListLength + blockVotes.size() *
                (FieldByteSize.blockHeight + FieldByteSize.hash) + FieldByteSize.identifier;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);

        Message.putString(nickname, buffer);
        buffer.putInt(port);

        buffer.put((byte) blockVotes.size());
        for (BlockVote vote : blockVotes) {
            buffer.putLong(vote.getHeight());
            buffer.put(vote.getHash());
        }

        buffer.put(newVerifierVote.getIdentifier());

        return array;
    }

    public static NodeJoinResponse fromByteBuffer(ByteBuffer buffer) {

        NodeJoinResponse result = null;

        try {
            String nickname = Message.getString(buffer);
            int port = buffer.getInt();

            List<BlockVote> blockVotes = new ArrayList<>();
            int numberOfVotes = Math.min(buffer.get(), maximumVotes);
            for (int i = 0; i < numberOfVotes; i++) {
                long height = buffer.getLong();
                byte[] hash = new byte[FieldByteSize.hash];
                buffer.get(hash);
                blockVotes.add(new BlockVote(height, hash));
            }

            byte[] newVerifierVoteIdentifier = new byte[FieldByteSize.identifier];
            buffer.get(newVerifierVoteIdentifier);
            NewVerifierVote newVerifierVote = new NewVerifierVote(newVerifierVoteIdentifier);

            result = new NodeJoinResponse(nickname, port, blockVotes, newVerifierVote);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }
}
