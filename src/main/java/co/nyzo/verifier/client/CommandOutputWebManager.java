package co.nyzo.verifier.client;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CommandOutputWebManager {

    private static final Long mapTimeout = 1000L * 60L * 30L;  // 30 minutes

    private static final Map<String, CommandOutputWeb> identifierToOutputMap = new ConcurrentHashMap<>();
    private static final Map<String, Long> identifierToTimestampMap = new ConcurrentHashMap<>();

    public static void register(CommandOutputWeb output) {
        identifierToOutputMap.put(output.getIdentifier(), output);
        identifierToTimestampMap.put(output.getIdentifier(), System.currentTimeMillis());
        cleanMaps();
    }

    public static void unregister(CommandOutputWeb output) {
        identifierToOutputMap.remove(output.getIdentifier());
        identifierToTimestampMap.remove(output.getIdentifier());
    }

    public static CommandOutputWeb get(String identifier) {
        return identifierToOutputMap.get(identifier);
    }

    private static void cleanMaps() {
        // Build a set of all identifiers to remove based on the map timeout.
        Set<String> identifiersToRemove = new HashSet<>();
        for (String identifier : identifierToTimestampMap.keySet()) {
            long timestamp = identifierToTimestampMap.getOrDefault(identifier, 0L);
            if (timestamp < System.currentTimeMillis() - mapTimeout) {
                identifiersToRemove.add(identifier);
            }
        }

        // Remove the identifiers from both maps.
        for (String identifier : identifiersToRemove) {
            identifierToOutputMap.remove(identifier);
            identifierToTimestampMap.remove(identifier);
        }
    }
}
