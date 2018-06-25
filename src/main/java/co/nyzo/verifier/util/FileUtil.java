package co.nyzo.verifier.util;

import java.io.File;
import java.nio.file.*;
import java.util.List;

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

    // These methods provide for atomic file writes. The file is written to a temporary location, then it is moved to
    // the final location atomically, replacing the existing file, if present.
    public static void writeFile(Path path, byte[] bytes) {

        Path temporaryPath = Paths.get(path.toAbsolutePath().toString() + "_temp");
        try {
            // Write the file to the temporary path then move it to the permanent path.
            Files.delete(temporaryPath);
            Files.write(temporaryPath, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Files.move(temporaryPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

        } catch (Exception ignored) {
            ignored.printStackTrace();
            NotificationUtil.sendOnce("unable to write file " + path.getFileName() + ": " + ignored.getMessage());
        }
    }

    public static void writeFile(Path path, List<String> lines) {

        Path temporaryPath = Paths.get(path.toAbsolutePath().toString() + "_temp");
        try {
            // Write the file to the temporary path then move it to the permanent path.
            Files.deleteIfExists(temporaryPath);
            Files.write(temporaryPath, lines, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Files.move(temporaryPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

        } catch (Exception ignored) { }
    }
}
