package co.nyzo.verifier;

import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class NicknameManager {

    private static final Map<ByteBuffer, String> nicknameMap = new HashMap<>();

    public static void put(byte[] identifier, String nickname) {

        if (nickname != null) {
            nickname = nickname.trim();
        }

        nicknameMap.put(ByteBuffer.wrap(identifier), nickname);
    }

    public static String get(byte[] identifier) {

        String nickname = nicknameMap.get(ByteBuffer.wrap(identifier));
        if (nickname == null || nickname.isEmpty()) {
            nickname = PrintUtil.compactPrintByteArray(identifier);
        }

        return nickname;
    }
}
