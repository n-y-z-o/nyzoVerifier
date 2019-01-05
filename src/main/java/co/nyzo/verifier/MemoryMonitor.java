package co.nyzo.verifier;

import co.nyzo.verifier.util.ThreadUtil;
import co.nyzo.verifier.util.UpdateUtil;

public class MemoryMonitor {

    static {
        start();
    }

    private static long sumMemory = 0L;
    private static long minimumMemory = Long.MAX_VALUE;
    private static long maximumMemory = 0L;
    private static int iterations = 0;

    private static void start() {

        new Thread(new Runnable() {
            @Override
            public void run() {

                while (!UpdateUtil.shouldTerminate()) {

                    long memory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                    sumMemory += memory;
                    minimumMemory = Math.min(minimumMemory, memory);
                    maximumMemory = Math.max(maximumMemory, memory);
                    iterations++;

                    ThreadUtil.sleep(3000L);
                }
            }
        }).start();
    }

    public static String getMemoryStats() {

        double minimumMemory = MemoryMonitor.minimumMemory / 1024.0 / 1024.0;
        double maximumMemory = MemoryMonitor.maximumMemory / 1024.0 / 1024.0;
        double averageMemory = sumMemory / Math.max(1.0, iterations) / 1024.0 / 1024.0;
        return String.format("%.1f MiB/%.1f MiB/%.1f MiB", minimumMemory, maximumMemory, averageMemory);
    }
}
