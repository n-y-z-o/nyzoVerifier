package co.nyzo.verifier.messages;

import co.nyzo.verifier.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class NodeJoinResponse implements MessageObject {

    private static final int maximumVotes = 100;

    private String nickname;
    private List<BlockVote> votes;

    public NodeJoinResponse() {

        this.nickname = Verifier.getNickname();
        this.votes = BlockVoteManager.getLocalVotes();
        limitVoteListSize();
    }

    public NodeJoinResponse(String nickname, List<BlockVote> votes) {

        this.nickname = nickname == null ? "" : nickname;
        this.votes = votes;
        limitVoteListSize();
    }

    private void limitVoteListSize() {

        while (votes.size() > maximumVotes) {
            votes.remove(votes.size() - 1);
        }
    }

    public String getNickname() {

        return nickname;
    }

    public List<BlockVote> getVotes() {

        return votes;
    }

    @Override
    public int getByteSize() {

        return FieldByteSize.string(nickname) + FieldByteSize.voteListLength + votes.size() *
                (FieldByteSize.blockHeight + FieldByteSize.hash);
    }

    @Override
    public byte[] getBytes() {

        byte[] array = new byte[getByteSize()];
        ByteBuffer buffer = ByteBuffer.wrap(array);

        byte[] nicknameBytes = nickname.getBytes(StandardCharsets.UTF_8);
        buffer.putShort((short) nicknameBytes.length);
        buffer.put(nicknameBytes);

        buffer.put((byte) votes.size());
        for (BlockVote vote : votes) {
            buffer.putLong(vote.getHeight());
            buffer.put(vote.getHash());
        }

        return array;
    }

    public static NodeJoinResponse fromByteBuffer(ByteBuffer buffer) {

        NodeJoinResponse result = null;

        try {
            short nicknameByteLength = buffer.getShort();
            byte[] nicknameBytes = new byte[nicknameByteLength];
            buffer.get(nicknameBytes);
            String nickname = new String(nicknameBytes, StandardCharsets.UTF_8);

            List<BlockVote> votes = new ArrayList<>();
            int numberOfVotes = Math.min(buffer.get(), maximumVotes);
            for (int i = 0; i < numberOfVotes; i++) {
                long height = buffer.getLong();
                byte[] hash = new byte[FieldByteSize.hash];
                buffer.get(hash);
                votes.add(new BlockVote(height, hash));
            }

            result = new NodeJoinResponse(nickname, votes);
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }

        return result;
    }
}
