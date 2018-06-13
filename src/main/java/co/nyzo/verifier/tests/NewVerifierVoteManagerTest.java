package co.nyzo.verifier.tests;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.NewVerifierVoteManager;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class NewVerifierVoteManagerTest {

    // This class tests to ensure that the new verifier vote manager tallies and ranks new verifiers properly.

    public static void main(String[] args) {

        // Three votes for ID 0.
        NewVerifierVoteManager.registerVote(id(0), id(0), false);
        NewVerifierVoteManager.registerVote(id(1), id(0), false);
        NewVerifierVoteManager.registerVote(id(2), id(0), false);

        // Two votes for ID 1.
        NewVerifierVoteManager.registerVote(id(3), id(1), false);
        NewVerifierVoteManager.registerVote(id(4), id(1), false);

        // Four votes for ID 2.
        NewVerifierVoteManager.registerVote(id(5), id(2), false);
        NewVerifierVoteManager.registerVote(id(6), id(2), false);
        NewVerifierVoteManager.registerVote(id(7), id(2), false);
        NewVerifierVoteManager.registerVote(id(8), id(2), false);
        NewVerifierVoteManager.registerVote(id(9), id(2), false);

        List<ByteBuffer> topVerifiers = NewVerifierVoteManager.topVerifiers();
        for (int i = 0; i < topVerifiers.size(); i++) {
            byte[] verifier = topVerifiers.get(i).array();
            System.out.println("verifier " + i + ": " + PrintUtil.compactPrintByteArray(verifier));
        }
    }

    private static byte[] id(int value) {

        byte[] result = new byte[FieldByteSize.identifier];
        result[FieldByteSize.identifier - 1] = (byte) value;

        return result;
    }
}
