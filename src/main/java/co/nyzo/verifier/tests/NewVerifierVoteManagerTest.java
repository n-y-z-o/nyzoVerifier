package co.nyzo.verifier.tests;

import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.NewVerifierVoteManager;
import co.nyzo.verifier.messages.NewVerifierVote;
import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.List;

public class NewVerifierVoteManagerTest {

    // This class tests to ensure that the new verifier vote manager tallies and ranks new verifiers properly.

    public static void main(String[] args) {

        // Three votes for ID 0.
        NewVerifierVoteManager.registerVote(id(0), vote(0), false);
        NewVerifierVoteManager.registerVote(id(1), vote(0), false);
        NewVerifierVoteManager.registerVote(id(2), vote(0), false);

        // Two votes for ID 1.
        NewVerifierVoteManager.registerVote(id(3), vote(1), false);
        NewVerifierVoteManager.registerVote(id(4), vote(1), false);

        // Four votes for ID 2.
        NewVerifierVoteManager.registerVote(id(5), vote(2), false);
        NewVerifierVoteManager.registerVote(id(6), vote(2), false);
        NewVerifierVoteManager.registerVote(id(7), vote(2), false);
        NewVerifierVoteManager.registerVote(id(8), vote(2), false);
        NewVerifierVoteManager.registerVote(id(9), vote(2), false);

        List<NewVerifierVote> topVerifiers = NewVerifierVoteManager.topVerifiers();
        printVerifiers(topVerifiers);

        // Remove all "old verifiers" from voting. In this test, this should clear the vote map complete.
        NewVerifierVoteManager.removeOldVotes();

        System.out.println("removed old votes");
        topVerifiers = NewVerifierVoteManager.topVerifiers();
        printVerifiers(topVerifiers);
    }

    private static byte[] id(int value) {

        byte[] identifier = new byte[FieldByteSize.identifier];
        identifier[FieldByteSize.identifier - 1] = (byte) value;

        return identifier;
    }

    private static NewVerifierVote vote(int value) {

        byte[] identifier = new byte[FieldByteSize.identifier];
        identifier[FieldByteSize.identifier - 1] = (byte) value;
        byte[] ipAddress = IpUtil.addressFromString("127.0.0.1");

        return new NewVerifierVote(identifier, ipAddress);
    }

    private static void printVerifiers(List<NewVerifierVote> verifiers) {

        if (verifiers.isEmpty()) {
            System.out.println("***** verifiers list is empty *****");
        } else {
            for (int i = 0; i < verifiers.size(); i++) {
                byte[] identifier = verifiers.get(i).getIdentifier();
                byte[] ipAddress = verifiers.get(i).getIpAddress();
                System.out.println("verifier " + i + ": " + PrintUtil.compactPrintByteArray(identifier) + ":" +
                        ipAddress);
            }
        }
    }
}
