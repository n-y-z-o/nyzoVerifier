package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;

import java.nio.ByteBuffer;

public class NodeJoinResponse implements MessageObject, PortMessage {

    private static final int maximumVotes = 100;

    private String nickname;
    private int port;
    private NewVerifierVote newVerifierVote;

    public NodeJoinResponse() {

        this.nickname = Verifier.getNickname();
        this.port = MeshListener.getPortTcp();
        this.newVerifierVote = NewVerifierVoteManager.getLocalVote();
    }

    public NodeJoinResponse(String nickname, int port, NewVerifierVote newVerifierNote) {

        this.nickname = nickname == null ? "" : nickname;
        this.port = port;
        this.newVerifierVote = newVerifierNote;
    }

    public String getNickname() {

        return nickname;
    }

    public int getPort() {
        return port;
    }

    public NewVerifierVote getNewVerifierVote() {
        return newVerifierVote;
    }

    @Override
    public int getByteSize() {

        int size = FieldByteSize.string(nickname) + FieldByteSize.port + FieldByteSize.voteListLength +
                FieldByteSize.identifier;

        return size;
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);

        Message.putString(nickname, buffer);
        buffer.putInt(port);

        // This response used to include block votes. This was not helpful, though, so this value indicates that zero
        // block votes are being sent.
        buffer.put((byte) 0);

        buffer.put(newVerifierVote.getIdentifier());

        return array;
    }

    public static NodeJoinResponse fromByteBuffer(ByteBuffer buffer) {

        NodeJoinResponse result = null;

        try {
            String nickname = Message.getString(buffer);
            int port = buffer.getInt();

            // This is for compatibility with older verifier versions. We no longer send or use block votes. If they
            // are included, read and discard them.
            int numberOfVotes = Math.min(buffer.get(), maximumVotes);
            for (int i = 0; i < numberOfVotes; i++) {
                BlockVote.fromByteBuffer(buffer);  // read and discard the vote
            }

            byte[] newVerifierVoteIdentifier = new byte[FieldByteSize.identifier];
            buffer.get(newVerifierVoteIdentifier);
            NewVerifierVote newVerifierVote = new NewVerifierVote(newVerifierVoteIdentifier);

            result = new NodeJoinResponse(nickname, port, newVerifierVote);
        } catch (Exception ignored) { }

        return result;
    }
}
