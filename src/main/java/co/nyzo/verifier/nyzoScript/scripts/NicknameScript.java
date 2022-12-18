package co.nyzo.verifier.nyzoScript.scripts;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.*;
import co.nyzo.verifier.nyzoScript.*;
import co.nyzo.verifier.util.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class NicknameScript implements NyzoScript {

    // This is the second NyzoScript. We want to implement a few different scripts within the repository before
    // attempting to generalize the mechanism. Of potential interest: this script will use binary (Base64) persistence
    // of state.

    // This script is going to move the nickname mechanism to the blockchain and allow all accounts to register
    // nicknames. These nicknames will also be consistently available to the client, which will improve user experience.


    @Override
    public NyzoScriptState update(NyzoScriptState inputState, List<Transaction> transactions) {

        // Get the existing data from the input state.
        Map<ByteBuffer, String> identifierToNicknameMap = nicknameMapFromByteBuffer(inputState == null ? null :
                ByteBuffer.wrap(inputState.getData()));

        // Add the nicknames provided by each transaction.
        for (Transaction transaction : transactions) {
            ByteBuffer identifier = ByteBuffer.wrap(transaction.getSenderIdentifier());
            String nickname = new String(transaction.getSenderData(), StandardCharsets.UTF_8).trim();
            if (!nickname.isEmpty()) {
                identifierToNicknameMap.put(identifier, nickname);
            }
        }

        // Return a state with data type and output data.
        return new NyzoScriptState(NyzoScriptStateContentType.Binary, bytesForNicknameMap(identifierToNicknameMap));
    }

    public byte[] bytesForNicknameMap(Map<ByteBuffer, String> map) {

        // Calculate the array length.
        int arrayLength = 0;
        for (ByteBuffer identifier : map.keySet()) {
            byte[] nicknameBytes = map.get(identifier).getBytes(StandardCharsets.UTF_8);
            arrayLength += 32 + 1 + nicknameBytes.length;
        }

        // The identifiers are 32 bytes each. The nicknames are stored as a single byte denoting length (limited to 32
        // bytes by the sender-data field) followed by the nickname bytes.
        byte[] array = new byte[arrayLength];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        for (ByteBuffer identifier : map.keySet()) {
            buffer.put(ByteUtil.ensureLength(identifier.array(), FieldByteSize.identifier));
            byte[] nicknameBytes = map.get(identifier).getBytes(StandardCharsets.UTF_8);
            buffer.put((byte) nicknameBytes.length);
            buffer.put(nicknameBytes);
        }

        return array;
    }

    private static Map<ByteBuffer, String> nicknameMapFromByteBuffer(ByteBuffer buffer) {

        Map<ByteBuffer, String> result = new HashMap<>();
        try {
            if (buffer != null) {
                while (buffer.position() < buffer.limit()) {
                    ByteBuffer identifier = ByteBuffer.wrap(Message.getByteArray(buffer, FieldByteSize.identifier));
                    String nickname = readNickname(buffer);
                    result.put(identifier, nickname);
                }
            }
        } catch (Exception e) {
            LogUtil.println(ConsoleColor.Red.backgroundBright() +
                    "exception in NicknameScript.nicknameMapFromByteBuffer(): " + e.getMessage() + ConsoleColor.reset);
        }

        return result;
    }

    private static String readNickname(ByteBuffer buffer) {
        int length = buffer.get();
        byte[] bytes = Message.getByteArray(buffer, length);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
