package co.nyzo.verifier.relay;

import co.nyzo.verifier.util.LogUtil;
import co.nyzo.verifier.util.ThreadUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class RelayEndpointManager {

    private static final Set<RelayEndpoint> endpoints = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean alive = new AtomicBoolean(false);

    public static void register(RelayEndpoint endpoint) {
        start();
        endpoints.add(endpoint);
    }

    public static void unregister(RelayEndpoint endpoint) {
        endpoints.remove(endpoint);
    }

    public static void start() {
        if (!alive.getAndSet(true)) {
            LogUtil.println("starting RelayEndpointManager");

            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (!UpdateUtil.shouldTerminate()) {
                        long loopStartTimestamp = System.currentTimeMillis();

                        // Refresh all registered endpoints.
                        for (RelayEndpoint endpoint : endpoints) {
                            endpoint.refresh();
                        }

                        // Ensure that the loop is no tighter than 1 second per iteration.
                        long sleepTime = Math.max(0L, loopStartTimestamp + 1000L - System.currentTimeMillis());
                        if (sleepTime > 0) {
                            ThreadUtil.sleep(sleepTime);
                        }
                    }

                    alive.set(false);
                }
            }).start();
        }
    }
}
