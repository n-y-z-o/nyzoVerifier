package co.nyzo.verifier;

import co.nyzo.verifier.util.IpUtil;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class BlacklistManager {

    private static final long blacklistDuration = 1000L * 60L * 10L;  // ten minutes
    private static final boolean useIpTables = false;

    private static final Map<ByteBuffer, Long> blacklistedAddresses = new HashMap<>();

    static {
        // Always try to flush firewall rules. This is necessary whether the firewall is being used this run or not,
        // because it might have been used the previous run.
        // `sudo iptables -nvL` to check
        //runProcess("sudo", "iptables", "-F");
    }

    public static void addToBlacklist(byte[] ipAddress) {

        if (BlockManager.isInitialized() && BlockManager.isCycleComplete() && !BlockManager.inGenesisCycle()) {

            ByteBuffer addressBuffer = ByteBuffer.wrap(ipAddress);
            if (!blacklistedAddresses.containsKey(addressBuffer)) {

                blacklistedAddresses.put(addressBuffer, System.currentTimeMillis());
                setIpTableEntry("-A", ipAddress);
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
                if (blacklistedAddresses.containsKey(ipAddress)) {
                    blacklistedAddresses.remove(ipAddress);
                    setIpTableEntry("-D", node.getIpAddress());
                }
            }
        }

        // Remove addresses that have expired.
        for (ByteBuffer address : new HashSet<>(blacklistedAddresses.keySet())) {
            if (System.currentTimeMillis() - blacklistedAddresses.getOrDefault(address, 0L) > blacklistDuration) {
                blacklistedAddresses.remove(address);
                setIpTableEntry("-D", address.array());
            }
        }
    }

    private static void setIpTableEntry(String addDrop, byte[] ipAddress) {

        //if (useIpTables) {
            //runProcess("sudo", "iptables", addDrop, "INPUT", "-s", IpUtil.addressAsString(ipAddress), "-p", "tcp",
            //        "--destination-port", MeshListener.getPort() + "", "-j", "DROP");
        //}
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
