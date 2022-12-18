package co.nyzo.verifier.nyzoScript;

import co.nyzo.verifier.*;
import co.nyzo.verifier.client.commands.TransactionForwardCommand;
import co.nyzo.verifier.nyzoScript.scripts.*;
import co.nyzo.verifier.nyzoString.*;
import co.nyzo.verifier.util.*;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class NyzoScriptManager {

    // This class is currently a minimal implementation to support the graffiti script. It will need to evolve
    // significantly to support scripts from all accounts.

    public static final File directory = new File(Verifier.dataRootDirectory, "script_states");
    private static final String highestBlockProcessedKey = "nyzo_script_manager_highest_block_processed";
    private static final Map<ByteBuffer, NyzoScript> scriptMap = new ConcurrentHashMap<>();
    private static Map<ByteBuffer, NyzoScriptState> unconfirmedStateMap = new ConcurrentHashMap<>();
    private static final AtomicBoolean alive = new AtomicBoolean(false);

    public static void start() {
        if (!alive.getAndSet(true)) {
            new Thread(() -> {
                while (!UpdateUtil.shouldTerminate()) {
                    // Sleep for 3 seconds to keep the loop from running too tightly.
                    ThreadUtil.sleep(3000L);

                    processUnconfirmedTransactions();
                }

                alive.set(false);
            }).start();
        }
    }

    public static void registerScript(NyzoStringPublicIdentifier identifier, NyzoScript script) {
        registerScript(identifier.getIdentifier(), script);
    }

    public static void registerScript(byte[] account, NyzoScript script) {
        scriptMap.put(ByteBuffer.wrap(account), script);

        // This is a temporary solution, and it needs to be considered more carefully. To provide structure, a script
        // should be given an opportunity to produce a state before it receives any transactions. This cannot be done in
        // the client's JVM for untrusted scripts.

        // If the account's state is null, run the script with no transactions to create an initial state.
        NyzoScriptState inputState = stateForAccount(ByteBuffer.wrap(account), false);
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

        String[] identifierStrings = { "id__8d63gfXfh3p9e._56sgEu_QPsWe54syae0PkELUw~VJDqvn-WALL",
                "id__8ezA79npaBzn430Y1fset6L5bZHcXhuVsYc0_1mEd8uatj3-NAMe" };
        NyzoScript[] scripts = { new GraffitiScript(), new NicknameScript() };
        for (int i = 0; i < identifierStrings.length; i++) {
            registerScript((NyzoStringPublicIdentifier) NyzoStringEncoder.decode(identifierStrings[i]), scripts[i]);
        }
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

    public static NyzoScriptState stateForAccount(ByteBuffer account, boolean includeUnconfirmedTransactions) {

        // First, look for a state that includes unconfirmed transactions if requested.
        NyzoScriptState state = null;
        if (includeUnconfirmedTransactions) {
            state = unconfirmedStateMap.get(account);
        }

        // If the state is not yet set, build it from the file.
        if (state == null) {
            state = NyzoScriptState.fromJsonString(stateJsonStringForAccount(account));
        }

        return state;
    }

    private static void processUnconfirmedTransactions() {

        // Get all unconfirmed transactions up to one block beyond the open edge.
        Collection<Transaction> allTransactions = TransactionForwardCommand.allTransactions();
        List<Transaction> transactionsInRange = new ArrayList<>();
        long thresholdHeight = BlockManager.openEdgeHeight(true) + 1L;
        for (Transaction transaction : allTransactions) {
            if (BlockManager.heightForTimestamp(transaction.getTimestamp()) <= thresholdHeight) {
                transactionsInRange.add(transaction);
            }
        }

        // Build a list for each recipient account. A script for an account is provided with all transactions received
        // by that account.
        Map<ByteBuffer, List<Transaction>> receiverToTransactionListMap = new HashMap<>();
        for (Transaction transaction : transactionsInRange) {
            ByteBuffer receiver = ByteBuffer.wrap(transaction.getReceiverIdentifier());
            List<Transaction> transactionsForAccount = receiverToTransactionListMap.computeIfAbsent(receiver,
                    key -> new ArrayList<>());
            transactionsForAccount.add(transaction);
        }

        // Create states with the unconfirmed transactions.
        Map<ByteBuffer, NyzoScriptState> newUnconfirmedStateMap = new ConcurrentHashMap<>();
        for (ByteBuffer receiver : receiverToTransactionListMap.keySet()) {
            try {
                long highestBlockProcessed = PersistentData.getLong(highestBlockProcessedKey,
                        BlockManager.getFrozenEdgeHeight() - 1L);
                NyzoScript script = scriptForAccount(receiver);
                if (script != null) {
                    // Get the current state for the account.
                    NyzoScriptState inputState = stateForAccount(receiver, false);

                    // Get the transactions for the account and sort in block order.
                    List<Transaction> transactions = receiverToTransactionListMap.get(receiver);
                    BalanceManager.sortTransactions(transactions);

                    // Remove transactions that are at or behind the highest block processed or have already been
                    // incorporated into the state.
                    if (inputState != null) {
                        while (!transactions.isEmpty() &&
                                BlockManager.heightForTimestamp(transactions.get(0).getTimestamp()) <=
                                        Math.max(highestBlockProcessed, inputState.getLastUpdateHeight())) {
                            transactions.remove(0);
                        }
                    }

                    // Process the transactions and store the state in the map.
                    if (!transactions.isEmpty()) {
                        // Process the transactions.
                        NyzoScriptState outputState = script.update(inputState, transactions);

                        // Create a new output state to ensure the managed fields are properly set.
                        long creationHeight = inputState == null ?
                                BlockManager.heightForTimestamp(transactions.get(0).getTimestamp()) :
                                inputState.getCreationHeight();
                        long lastUpdateHeight = BlockManager.heightForTimestamp(transactions.get(transactions.size() -
                                1).getTimestamp());
                        NyzoScriptState managedState = new NyzoScriptState(creationHeight, lastUpdateHeight,
                                outputState.getContentType(), true, outputState.getData());

                        // Store the managed state in the map.
                        newUnconfirmedStateMap.put(receiver, managedState);
                    }
                }
            } catch (Exception e) {
                LogUtil.println("exception processing script for receiver " +
                        ByteUtil.arrayAsStringWithDashes(receiver.array()) + " for pending transactions");
            }
        }

        // Set the map for an atomic update of states.
        unconfirmedStateMap = newUnconfirmedStateMap;
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
                    NyzoScriptState inputState = stateForAccount(receiver, false);

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
