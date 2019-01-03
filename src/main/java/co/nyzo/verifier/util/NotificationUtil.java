package co.nyzo.verifier.util;

import co.nyzo.verifier.Verifier;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NotificationUtil {

    private static long lastEarnedTimestamp = 0L;
    private static final Set<Integer> sendOnceNotifications = new HashSet<>();

    private static final String endpoint = loadFromFile("endpoint", "");
    private static final int maximumBudget = loadFromFile("budget", 10);
    private static int currentBudget = -1;
    private static final long earnInterval = loadFromFile("interval", 1000 * 60 * 2);

    public static int getCurrentBudget() {

        return currentBudget;
    }

    public static void send(String message) {

        send(message, -1L);
    }

    public static void send(String message, long height) {

        if (endpoint == null || endpoint.isEmpty()) {

            System.out.println(message);

        } else {

            // Our maximum/initial budget is 10 notifications, and we earn a new notification every 2 minutes. Because
            // the last-earned timestamp starts at zero, the budget be set to maximum the first time this executes.
            long notificationsEarned = (System.currentTimeMillis() - lastEarnedTimestamp) / earnInterval;
            if (notificationsEarned > 0) {
                currentBudget = (int) Math.min(maximumBudget, currentBudget + notificationsEarned);
                lastEarnedTimestamp += notificationsEarned * earnInterval;
            }

            if (currentBudget > 0) {

                currentBudget--;
                if (currentBudget == 0) {
                    message += " *Message limit reached on " + Verifier.getNickname() + "*";
                }

                try {
                    String jsonString = "{\"text\":\"" + message + "\"";
                    if (height >= 0) {
                        jsonString += ",\"height\":\"" + height + "\"";
                    }
                    jsonString += "}";
                    System.out.println(jsonString);

                    URL url = new URL(endpoint);
                    HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                    connection.setDoOutput(true);
                    connection.setDoInput(true);
                    connection.setRequestProperty("Content-type", "application/json");
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setRequestMethod("POST");

                    OutputStreamWriter outputStreamWriter = new OutputStreamWriter(connection.getOutputStream());
                    outputStreamWriter.write(jsonString);
                    outputStreamWriter.flush();

                    StringBuilder result = new StringBuilder();
                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(),
                                "UTF-8"));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            result.append(line + "\n");
                        }
                        reader.close();

                        System.out.println(result.toString());
                    } else {
                        System.out.println(connection.getResponseMessage());
                    }
                } catch (Exception ignored) { }
            } else {
                System.out.println(height + ": " + message);
            }
        }
    }

    public static void sendOnce(String message) {

        int hashCode = message.hashCode();
        if (!sendOnceNotifications.contains(hashCode)) {
            sendOnceNotifications.add(hashCode);
            send(message);
        }
    }

    private static String loadFromFile(String name, String defaultValue) {

        String value = null;
        try {
            List<String> fileContents = Files.readAllLines(Paths.get(Verifier.dataRootDirectory.getAbsolutePath() +
                    "/notification_config"));
            for (String line : fileContents) {
                if (line.startsWith(name + "=")) {
                    value = line.substring(name.length() + 1).trim();
                }
            }
        } catch (Exception ignored) { }

        return value;
    }

    private static int loadFromFile(String name, int defaultValue) {

        int value = defaultValue;
        try {
            value = Integer.parseInt(loadFromFile(name, defaultValue + ""));
        } catch (Exception ignored) { }

        return value;
    }
}
