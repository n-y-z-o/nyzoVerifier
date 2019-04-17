package co.nyzo.verifier.util;

import co.nyzo.verifier.BlockManager;
import co.nyzo.verifier.MeshListener;
import co.nyzo.verifier.SeedTransactionManager;
import co.nyzo.verifier.Verifier;

public class UpdateUtil {

    private static boolean shouldTerminate = false;

    public static boolean shouldTerminate() {

        return shouldTerminate;
    }

    public static void terminate() {

        System.out.println("termination requested");
        shouldTerminate = true;
    }

    public static void terminateAfterDelay(long delayMilliseconds) {

        try {
            Thread.sleep(delayMilliseconds);
        } catch (Exception ignored) { }

        terminate();
    }

    public static void reset() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                // Flag that the system should terminate and close the MeshListener socket.
                terminate();
                MeshListener.closeSockets();

                // Wait for the verifier, the mesh listener, and the seed transaction generator to terminate.
                while (Verifier.isAlive() || MeshListener.isAlive() || SeedTransactionManager.isAlive()) {
                    try {
                        Thread.sleep(300L);
                        System.out.println("waiting for termination: v=" + Verifier.isAlive() + ", m=" +
                                MeshListener.isAlive());
                    } catch (Exception ignored) { }
                }

                // Delete the block directory and the seed transaction directory.
                FileUtil.delete(BlockManager.blockRootDirectory);
                FileUtil.delete(SeedTransactionManager.rootDirectory);

                // Exit the application.
                System.exit(0);
            }
        }).start();
    }
}
