package co.nyzo.verifier;

import co.nyzo.verifier.util.IpUtil;
import co.nyzo.verifier.util.LogUtil;
import co.nyzo.verifier.util.PreferencesUtil;

import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NodeBanManager {

    private static final String trackDurationMinutesKey = "node_track_duration_minutes";
    private static final int trackDurationMinutes = PreferencesUtil.getInt(trackDurationMinutesKey, 20);
    private static final long trackDuration = 1000L * 60L * trackDurationMinutes;

    private static final String trackMaxCountersKey = "node_track_max_counters";
    private static final int trackMaxCounters = PreferencesUtil.getInt(trackMaxCountersKey, 2000);

    private static final String maintenanceMinutesKey = "node_maintenance_minutes";
    private static final int maintenanceMinutes = PreferencesUtil.getInt(maintenanceMinutesKey, 5);
    private static final long maintenancePeriod = 1000L * 60L * maintenanceMinutes;

    private static final String banThresholdKey = "node_ban_threshold";
    private static final int banThreshold = PreferencesUtil.getInt(banThresholdKey, 0);

    private static final String banDurationMinutesKey = "node_ban_duration_minutes";
    private static final int banDurationMinutes = PreferencesUtil.getInt(banDurationMinutesKey, 10);
    private static final long banDuration = 1000L * 60L * banDurationMinutes;

    private static final Map<ByteBuffer, Map.Entry<Integer, Long>> trackCounters = new ConcurrentHashMap<>(2000);
    private static final Map<ByteBuffer, Long> bannedAddresses = new ConcurrentHashMap<>();

    private static Long lastMaintenance = 0L;

    public static boolean isActive() {
        return banThreshold > 0;
    }

    public static synchronized void trackJoin(ByteBuffer ipAddress) {

        // Skip tracking if disabled.
        if (!isActive()) {
            return;
        }

        if (BlockManager.completedInitialization() && BlockManager.isCycleComplete() &&
                !BlockManager.inGenesisCycle()) {

            if (trackCounters.containsKey(ipAddress)) {

                Map.Entry<Integer, Long> counter = trackCounters.get(ipAddress);
                int count = counter.getKey();
                long timestamp = counter.getValue();

                if (count > banThreshold) {

                    LogUtil.println("nodejoin_ban " + IpUtil.addressAsString(ipAddress.array()));
                    addToBanlist(ipAddress);
                    trackCounters.remove(ipAddress);
                } else {
                    count = !isLapsed(timestamp, trackDuration) ? count + 1 : 1;
                    trackCounters.put(ipAddress, new AbstractMap.SimpleEntry<>(count, System.currentTimeMillis()));
                }
            } else {
                // System.out.println("Started tracking nodejoin counts for " + IpUtil.addressAsString(ipAddress.array()));
                trackCounters.put(ipAddress, new AbstractMap.SimpleEntry<>(1, System.currentTimeMillis()));
            }

            // If the tracking counters exceeds configured maximum, perform maintenance.
            if (trackCounters.size() > trackMaxCounters && isLapsed(lastMaintenance, maintenancePeriod)) {
                performMaintenance();
            }
        }
    }

    public static synchronized void trackJoin(byte[] ipAddress) {
        trackJoin(ByteBuffer.wrap(ipAddress));
    }

    public static boolean inBanlist(ByteBuffer ipAddress) {

        // If bans are permanent, only check if address exists in list.
        if (banDuration <= 0 && bannedAddresses.containsKey(ipAddress)) {
            return true;
        }

        long timestamp = bannedAddresses.getOrDefault(ipAddress, 0L);
        return !isLapsed(timestamp, banDuration);
    }

    public static boolean inBanlist(byte[] ipAddress) {
        return inBanlist(ByteBuffer.wrap(ipAddress));
    }

    public static int getBanlistSize() {
        return bannedAddresses.size();
    }

    public static void performMaintenance() {

        // Skip maintenance if disabled.
        if (!isActive()) {
            return;
        }

        // System.out.println("Starting node ban maintenance");
        lastMaintenance = System.currentTimeMillis();

        // Remove expired entries from counter list.
        for (Map.Entry<ByteBuffer, Map.Entry<Integer, Long>> entry : new HashSet<>(trackCounters.entrySet())) {
            Map.Entry<Integer, Long> track = entry.getValue();
            long timestamp = track.getValue();

            if (isLapsed(timestamp, trackDuration)) {
                // System.out.println("Stopped tracking nodejoin counts (Expired) for " + IpUtil.addressAsString(entry.getKey().array()));
                trackCounters.remove(entry.getKey());
            }
        }

        // Remove addresses of any nodes in the current cycle.
        for (Node node : NodeManager.getMesh()) {
            if (BlockManager.verifierInOrNearCurrentCycle(ByteBuffer.wrap(node.getIdentifier()))) {
                ByteBuffer ipAddress = ByteBuffer.wrap(node.getIpAddress());
                bannedAddresses.remove(ipAddress);
            }
        }

        // Skip address removal as bans are permanent.
        if (banDuration <= 0) {
            return;
        }

        // Remove expired entries from ban list.
        for (ByteBuffer address : new HashSet<>(bannedAddresses.keySet())) {
            if (isLapsed(bannedAddresses.getOrDefault(address, 0L), banDuration)) {
                LogUtil.println("nodejoin_unban " + IpUtil.addressAsString(address.array()));
                bannedAddresses.remove(address);
            }
        }
    }

    private static void addToBanlist(ByteBuffer ipAddress) {

        if (!bannedAddresses.containsKey(ipAddress)) {
            bannedAddresses.put(ipAddress, System.currentTimeMillis());
        }
    }

    private static boolean isLapsed(Long timestamp, Long duration) {
        return System.currentTimeMillis() > timestamp + duration;
    }
}
