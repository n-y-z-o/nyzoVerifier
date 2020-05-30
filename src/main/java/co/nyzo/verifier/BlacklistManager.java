package co.nyzo.verifier;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class BlacklistManager {

    private static final long blacklistDuration = 1000L * 60L * 10L;  // ten minutes
    private static final Map<ByteBuffer, Long> blacklistedAddresses = new HashMap<>();

    public static void addToBlacklist(byte[] ipAddress) {

        if (BlockManager.completedInitialization() && BlockManager.isCycleComplete() &&
                !BlockManager.inGenesisCycle()) {

            ByteBuffer addressBuffer = ByteBuffer.wrap(ipAddress);
            if (!blacklistedAddresses.containsKey(addressBuffer)) {
                blacklistedAddresses.put(addressBuffer, System.currentTimeMillis());
            }
        }
    }

    public static boolean inBlacklist(ByteBuffer ipAddress) {
        long timestamp = blacklistedAddresses.getOrDefault(ipAddress, 0L);
        return System.currentTimeMillis() < timestamp + blacklistDuration;
    }

    public static boolean inBlacklist(byte[] ipAddress) {
        return inBlacklist(ByteBuffer.wrap(ipAddress));
    }

    public static int getBlacklistSize() {
        return blacklistedAddresses.size();
    }

    public static void performMaintenance() {

        // Remove addresses of any nodes in the current cycle.
        for (Node node : NodeManager.getMesh()) {
            if (BlockManager.verifierInOrNearCurrentCycle(ByteBuffer.wrap(node.getIdentifier()))) {
                ByteBuffer ipAddress = ByteBuffer.wrap(node.getIpAddress());
                blacklistedAddresses.remove(ipAddress);
            }
        }

        // Remove addresses that have expired.
        for (ByteBuffer address : new HashSet<>(blacklistedAddresses.keySet())) {
            if (System.currentTimeMillis() - blacklistedAddresses.getOrDefault(address, 0L) > blacklistDuration) {
                blacklistedAddresses.remove(address);
            }
        }
    }

    private static void runProcess(String... args) {

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(args);
            Process process = processBuilder.start();
            readStream(process.getInputStream(), System.out);
            readStream(process.getErrorStream(), System.err);

            while (process.isAlive()) {
                try {
                    Thread.sleep(50L);
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
    }

    private static void readStream(InputStream inputStream, PrintStream outputStream) {

        BufferedReader outputReader = new BufferedReader(new InputStreamReader(inputStream));

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String line;
                    while ((line = outputReader.readLine()) != null) {
                        outputStream.println(line);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
