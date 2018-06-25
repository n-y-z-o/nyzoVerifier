package co.nyzo.verifier;

import co.nyzo.verifier.util.FileUtil;
import co.nyzo.verifier.util.PrintUtil;
import co.nyzo.verifier.util.UpdateUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class BlockFileConsolidator {

    public static void start() {

        new Thread(new Runnable() {
            @Override
            public void run() {

                while (!UpdateUtil.shouldTerminate()) {

                    consolidateFiles();

                    // Sleep for 5 minutes (300 seconds) in 3-second intervals.
                    for (int i = 0; i < 100; i++) {
                        if (!UpdateUtil.shouldTerminate()) {
                            try {
                                Thread.sleep(3000L);
                            } catch (Exception ignored) { }
                        }
                    }
                }
            }
        }).start();
    }

    private static void consolidateFiles() {

        // To prevent unbounded work on this, we will only look at the last 5000 blocks.

    }

}
