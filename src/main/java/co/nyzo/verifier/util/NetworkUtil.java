package co.nyzo.verifier.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

class NetworkUtil {

    public static String stringForUrl(String urlString, int timeoutMilliseconds) {

        StringBuilder result = new StringBuilder();
        try {
            URL url = new URL(urlString);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(timeoutMilliseconds);

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }

            reader.close();
        } catch (Exception ignored) { }

        return result.toString();
    }
}
