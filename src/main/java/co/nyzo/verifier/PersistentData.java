package co.nyzo.verifier;

import co.nyzo.verifier.util.FileUtil;
import co.nyzo.verifier.util.PrintUtil;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PersistentData {

    private static final Map<String, String> dataMap = new ConcurrentHashMap<>();
    private static final File file = new File(Verifier.dataRootDirectory, "persistent_data");

    static {
        loadFile();
    }

    private static void loadFile() {

        Path path = Paths.get(file.getAbsolutePath());
        if (path.toFile().exists()) {
            try {
                List<String> contentsOfFile = Files.readAllLines(path);
                for (String line : contentsOfFile) {
                    try {
                        line = line.trim();
                        int indexOfHash = line.indexOf("#");
                        if (indexOfHash >= 0) {
                            line = line.substring(0, indexOfHash).trim();
                        }
                        String[] split = line.split("=");
                        if (split.length == 2) {
                            dataMap.put(split[0].trim().toLowerCase(), split[1].trim().toLowerCase());
                        }
                    } catch (Exception e) {
                        System.out.println("issue loading line from persistent data file: " + line);
                    }
                }
            } catch (Exception e) {
                System.out.println("issue getting persistent data file: " + PrintUtil.printException(e));
            }
        } else {
            System.out.println("skipping persistent data loading; file not present");
        }
    }

    private static void writeFile() {

        // Despite the simplicity of structure, the process of putting the data in the map and writing the file is
        // thread-safe. Each put operation triggers a writing of the file, and the file writes are effectively atomic.
        // Of course, as a file write is involved, and as each file write rewrites the entire file, care should be
        // taken to only use this for small amounts of data that change infrequently.
        List<String> lines = new ArrayList<>();
        for (String key : dataMap.keySet()) {
            lines.add(key + "=" + dataMap.get(key));
        }

        FileUtil.writeFile(Paths.get(file.getAbsolutePath()), lines);
    }

    public static String get(String key) {

        return dataMap.getOrDefault(key.toLowerCase(), "");
    }

    public static boolean getBoolean(String key, boolean defaultValue) {

        boolean result = defaultValue;
        try {
            String preference = dataMap.get(key.toLowerCase());
            if (preference != null && preference.equals("1")) {
                result = true;
            } else if (preference != null && preference.equals("0")) {
                result = false;
            }
        } catch (Exception ignored) { }

        return result;
    }

    public static int getInt(String key, int defaultValue) {

        int result = defaultValue;
        try {
            String preference = dataMap.get(key.toLowerCase());
            if (preference != null && !preference.isEmpty()) {
                result = Integer.parseInt(preference);
            }
        } catch (Exception ignored) { }

        return result;
    }

    public static long getLong(String key, long defaultValue) {

        long result = defaultValue;
        try {
            String preference = dataMap.get(key.toLowerCase());
            if (preference != null && !preference.isEmpty()) {
                result = Long.parseLong(preference);
            }
        } catch (Exception ignored) { }

        return result;
    }

    public static byte[] getByteArray(String key, int length, byte[] defaultValue) {

        byte[] result = defaultValue;
        try {
            String preference = dataMap.get(key.toLowerCase());
            if (preference != null && preference.length() >= length * 2) {
                result = ByteUtil.byteArrayFromHexString(preference, length);
            }
        } catch (Exception ignored) { }

        return result;
    }

    public static void put(String key, String value) {

        dataMap.put(key, value);
        writeFile();
    }

    public static void put(String key, boolean value) {

        dataMap.put(key, value ? "1" : "0");
        writeFile();
    }

    public static void put(String key, int value) {

        dataMap.put(key, value + "");
        writeFile();
    }

    public static void put(String key, long value) {

        dataMap.put(key, value + "");
        writeFile();
    }

    public static void put(String key, byte[] value) {

        dataMap.put(key, ByteUtil.arrayAsStringWithDashes(value));
        writeFile();
    }
}
