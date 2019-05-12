package co.nyzo.verifier;

import co.nyzo.verifier.util.PreferencesUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NicknameManager {

    private static final String mapLimitingThresholdKey = "nickname_map_threshold";
    private static final int mapLimitingThreshold = PreferencesUtil.getInt(mapLimitingThresholdKey, 2000);
    static {
        // Display the map limiting threshold so the operator of the verifier can ensure it was loaded properly.
        System.out.println("NicknameManager.mapLimitingThreshold=" + mapLimitingThreshold);
    }

    private static final int maximumNicknameLength = 32;

    private static final Map<ByteBuffer, String> nicknameMap = new ConcurrentHashMap<>();

    public static void put(byte[] identifier, String nickname) {

        // If the nickname map has more entries than the threshold, accept nicknames from in-cycle verifiers only. Even
        // if all nicknames up to the threshold are from out-of-cycle verifiers, the maximum map size will be
        // threshold + [cycle size], and cycle size is guaranteed by the blockchain rules to grow slowly. This will
        // protect against memory issues both from deliberate attacks and large numbers of out-of-cycle verifiers. Also,
        // do not process null nicknames or absurdly long nicknames.
        if (nickname != null && nickname.length() < maximumNicknameLength &&
                (nicknameMap.size() < mapLimitingThreshold ||
                        BlockManager.verifierInCurrentCycle(ByteBuffer.wrap(identifier)))) {

            nickname = nickname.trim();
            nicknameMap.put(ByteBuffer.wrap(identifier), nickname);
        }
    }

    public static String get(byte[] identifier) {

        String nickname;
        if (identifier == null) {
            nickname = "(null)";
        } else {
            nickname = nicknameMap.get(ByteBuffer.wrap(identifier));
            if (nickname == null || nickname.isEmpty()) {
                nickname = PrintUtil.compactPrintByteArray(identifier);
            }
        }

        return nickname;
    }
}
