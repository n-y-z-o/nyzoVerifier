package co.nyzo.verifier.util;

import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.Verifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PreferencesUtil {

    private static final Map<String, String> preferences = new ConcurrentHashMap<>();
    static {
        loadPreferences();
    }

    private static void loadPreferences() {

        Path path = Paths.get(Verifier.dataRootDirectory.getAbsolutePath() + "/preferences");
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
                        int splitIndex = line.indexOf("=");
                        if (splitIndex > 0) {
                            String key = line.substring(0, splitIndex).trim().toLowerCase();
                            String value = line.substring(splitIndex + 1).trim();
                            preferences.put(key, value);
                        }
                    } catch (Exception e) {
                        System.out.println("issue loading line from preferences: " + line);
                    }
                }
            } catch (Exception e) {
                System.out.println("issue getting preferences: " + PrintUtil.printException(e));
            }
        } else {
            System.out.println("skipping preferences loading; file not present");
        }
    }

    public static void reloadPreferences() {
        preferences.clear();
        loadPreferences();
    }

    public static String get(String key) {

        return preferences.getOrDefault(key.toLowerCase(), "");
    }

    public static boolean getBoolean(String key, boolean defaultValue) {

        boolean result = defaultValue;
        try {
            String preference = preferences.get(key.toLowerCase());
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
            String preference = preferences.get(key.toLowerCase());
            if (preference != null && !preference.isEmpty()) {
                result = Integer.parseInt(preference);
            }
        } catch (Exception ignored) { }

        return result;
    }

    public static long getLong(String key, long defaultValue) {

        long result = defaultValue;
        try {
            String preference = preferences.get(key.toLowerCase());
            if (preference != null && !preference.isEmpty()) {
                result = Long.parseLong(preference);
            }
        } catch (Exception ignored) { }

        return result;
    }

    public static byte[] getByteArray(String key, int length, byte[] defaultValue) {

        byte[] result = defaultValue;
        try {
            String preference = preferences.get(key.toLowerCase());
            if (preference != null && preference.length() >= length * 2) {
                result = ByteUtil.byteArrayFromHexString(preference, length);
            }
        } catch (Exception ignored) { }

        return result;
    }

    public static double getDouble(String key, double defaultValue) {

        double result = defaultValue;
        try {
            String preference = preferences.get(key.toLowerCase());
            if (preference != null && !preference.isEmpty()) {
                result = Double.parseDouble(preference);
            }
        } catch (Exception ignored) { }

        return result;
    }
}
