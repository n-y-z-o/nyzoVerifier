package co.nyzo.verifier.util;

import java.io.File;

public class FileUtil {

    public static void delete(File file) {

        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    delete(child);
                }
            }
        }
        file.delete();
    }
}
