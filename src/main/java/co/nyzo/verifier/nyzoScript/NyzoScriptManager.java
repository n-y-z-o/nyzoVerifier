package co.nyzo.verifier.nyzoScript;

import co.nyzo.verifier.*;
import co.nyzo.verifier.nyzoString.*;
import co.nyzo.verifier.util.*;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NyzoScriptManager {

    // This class is currently a minimal implementation to support the graffiti script. It will need to evolve
    // significantly to support scripts from all accounts.

    public static final File directory = new File(Verifier.dataRootDirectory, "script_states");
    private static final String highestBlockProcessedKey = "nyzo_script_manager_highest_block_processed";
    private static final Map<ByteBuffer, NyzoScript> scriptMap = new ConcurrentHashMap<>();

    public static void registerScript(NyzoStringPublicIdentifier identifier, NyzoScript script) {
        registerScript(identifier.getIdentifier(), script);
    }

    public static void registerScript(byte[] account, NyzoScript script) {
        scriptMap.put(ByteBuffer.wrap(account), script);

        // This is a temporary solution, and it needs to be considered more carefully. To provide structure, a script
        // should be given an opportunity to produce a state before it receives any transactions. This cannot be done in
        // the client's JVM for untrusted scripts.

        // If the account's state is null, run the script with no transactions to create an initial state.
        NyzoScriptState inputState = stateForAccount(ByteBuffer.wrap(account));
        if (inputState == null) {
            // Process the transactions.
            NyzoScriptState outputState = script.update(inputState, Collections.emptyList());

            // Create a new output state to ensure the managed fields are properly set.
            long frozenEdgeHeight = BlockManager.getFrozenEdgeHeight();
            NyzoScriptState managedState = new NyzoScriptState(frozenEdgeHeight, frozenEdgeHeight,
                    outputState.getContentType(), false, outputState.getData());

            // Write the managed state to a file.
            FileUtil.writeFile(Paths.get(stateFileForAccount(account).getAbsolutePath()),
                    managedState.renderJson().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void registerScripts() {
        // Ensure the state directory exists.
        directory.mkdirs();

        String identifierString = "id__8d63gfXfh3p9e._56sgEu_QPsWe54syae0PkELUw~VJDqvn-WALL";
        registerScript((NyzoStringPublicIdentifier) NyzoStringEncoder.decode(identifierString), new GraffitiScript());
    }

    public static NyzoScript scriptForAccount(ByteBuffer account) {
        if (scriptMap.isEmpty()) {
            registerScripts();
        }

        return scriptMap.get(account);
    }

    public static String stateJsonStringForAccount(ByteBuffer account) {

        // Read the state from the file. For simplicity, states are always read from files. If caching would be helpful
        // to improve performance for frequently updated or frequently queried scripts, such caching can be added later
        // and consideration of threading issues will be considered at that time.

        // Get the bytes from the file.
        File stateFile = stateFileForAccount(account.array());
        byte[] fileContents = new byte[0];
        if (stateFile.exists()) {
            try {
                fileContents = Files.readAllBytes(Paths.get(stateFile.getAbsolutePath()));
            } catch (Exception ignored) { }
        }

        // Make a JSON string from the bytes. All states are stored as JSON, even those with binary data.
        return new String(fileContents, StandardCharsets.UTF_8);
    }

    public static NyzoScriptState stateForAccount(ByteBuffer account) {
        return NyzoScriptState.fromJsonString(stateJsonStringForAccount(account));
    }

    public static void processBlock(Block block) {
        if (block != null && block.getBlockHeight() > PersistentData.getLong(highestBlockProcessedKey, -1L)) {
            processBlockInternal(block);
            PersistentData.put(highestBlockProcessedKey, block.getBlockHeight());
        }
    }

    public static void processBlockInternal(Block block) {
        // Build a list for each recipient account. A script for an account is provided with all transactions received
        // by that account.
        Map<ByteBuffer, List<Transaction>> receiverToTransactionListMap = new HashMap<>();
        for (Transaction transaction : block.getTransactions()) {
            ByteBuffer receiver = ByteBuffer.wrap(transaction.getReceiverIdentifier());
            List<Transaction> transactionsForAccount = receiverToTransactionListMap.computeIfAbsent(receiver,
                    key -> new ArrayList<>());
            transactionsForAccount.add(transaction);
        }

        // Process each transaction list for which a script is available.
        for (ByteBuffer receiver : receiverToTransactionListMap.keySet()) {
            try {
                NyzoScript script = scriptForAccount(receiver);
                if (script != null) {
                    // Get the current state for the account.
                    NyzoScriptState inputState = stateForAccount(receiver);

                    // Do not process a block that has already been processed for this script.
                    if (inputState == null || inputState.getLastUpdateHeight() < block.getBlockHeight()) {
                        // Process the transactions.
                        NyzoScriptState outputState = script.update(inputState,
                                receiverToTransactionListMap.get(receiver));

                        // Create a new output state to ensure the managed fields are properly set.
                        long creationHeight = inputState == null ? block.getBlockHeight() :
                                inputState.getCreationHeight();
                        long lastUpdateHeight = block.getBlockHeight();
                        NyzoScriptState managedState = new NyzoScriptState(creationHeight, lastUpdateHeight,
                                outputState.getContentType(), false, outputState.getData());

                        // Write the managed state to a file.
                        FileUtil.writeFile(Paths.get(stateFileForAccount(receiver.array()).getAbsolutePath()),
                                managedState.renderJson().getBytes(StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception e) {
                LogUtil.println("exception processing script for receiver " +
                        ByteUtil.arrayAsStringWithDashes(receiver.array()) + " at height " + block.getBlockHeight());
            }
        }
    }

    private static File stateFileForAccount(byte[] account) {
        return new File(directory, ByteUtil.arrayAsStringWithDashes(account) + ".nyzoscriptstate");
    }
}
