package co.nyzo.verifier;

import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NicknameManager {

    private static final int maximumNicknameLength = 32;

    private static final Map<ByteBuffer, String> nicknameMap = new ConcurrentHashMap<>();

    public static void put(byte[] identifier, String nickname) {

        // If the nickname map has more than 2000 entries, accept nicknames from in-cycle verifiers only. Even if the
        // first 2000 nicknames are all from out-of-cycle verifiers, the maximum map size will be 2000 + [cycle size],
        // and cycle size is guaranteed by the blockchain rules to grow slowly. This will protect against memory issues
        // both from deliberate attacks and large numbers of out-of-cycle verifiers. Also, do not process null nicknames
        // or absurdly long nicknames.
        if (nickname != null && nickname.length() < maximumNicknameLength &&
                (nicknameMap.size() < 2000 || BlockManager.verifierInCurrentCycle(ByteBuffer.wrap(identifier)))) {

            nickname = nickname.trim();
            nicknameMap.put(ByteBuffer.wrap(identifier), nickname);
        }
    }

    public static String get(byte[] identifier) {

        String nickname = nicknameMap.get(ByteBuffer.wrap(identifier));
        if (nickname == null || nickname.isEmpty()) {
            nickname = PrintUtil.compactPrintByteArray(identifier);
        }

        return nickname;
    }
}
