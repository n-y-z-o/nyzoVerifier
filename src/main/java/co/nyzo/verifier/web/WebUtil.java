package co.nyzo.verifier.web;

import co.nyzo.verifier.NicknameManager;
import co.nyzo.verifier.util.PrintUtil;

import java.util.concurrent.atomic.AtomicInteger;

public class WebUtil {

    public static final String shrug = " \u00AF\\_(\u30C4)_/\u00AF ";
    public static final String disapprove = "\u0CA0_\u0CA0";

    private static final String buttonStyle = "border: 1px solid; border-radius: 0.5rem; padding: 0.5rem; " +
            "text-decoration: none; color: white; margin: auto; cursor: pointer; user-select: none;";
    public static final String acceptButtonStyle = buttonStyle + " border-color: #080; background-color: #484;";
    public static final String cancelButtonStyle = buttonStyle + "border-color: #f00; background-color: #f44;";

    private static final AtomicInteger nextId = new AtomicInteger(0);

    public static String sanitizedNickname(byte[] identifier) {

        // Get the nickname, sanitize it, remove numeral strings, and trim whitespace.
        String nickname = NicknameManager.get(identifier);
        nickname = sanitizeString(nickname);
        nickname = removeNumeralStrings(nickname);
        nickname = nickname.trim();

        // If the nickname is over 22 characters, shorten it to 10 characters with an ellipsis.
        if (nickname.length() > 22) {
            nickname = nickname.substring(0, 10) + "...";
        }

        // If the resulting sanitized nickname is empty, replace it with the compact byte array.
        if (nickname.isEmpty()) {
            nickname = PrintUtil.compactPrintByteArray(identifier);
        }

        return nickname;
    }

    public static String removeNumeralStrings(String string) {

        // Count the numerals in the string.
        int numeralCount = 0;
        char[] characters = string.toCharArray();
        for (int i = 0; i < characters.length; i++) {
            if (characters[i] >= '0' && characters[i] <= '9') {
                numeralCount++;
            }
        }

        // If there are more than 8 numerals in the string, and the string does not appear to contain an IP address,
        // mask out the numerals. This removes most phone numbers and credit-card numbers.
        if (numeralCount > 8) {
            if (!containsIp(string)) {
                string = string.replaceAll("[0-9]", "*");
            }
        }

        return string;
    }

    public static String sanitizeString(String string) {

        string = string.trim();
        if (string.toLowerCase().contains("<script>") || string.toLowerCase().contains("javascript") ||
                string.toLowerCase().contains("alert")) {
            string = shrug;
        }

        if (string.contains("<") || string.contains(">")) {
            string = disapprove;
        }

        string = string.replace('\u2190', ' ');  // left arrow
        string = string.replace('\u2192', ' ');  // right arrow
        string = string.replace('\ufdfd', '\ufdf2');  // Bismillah (prayer in a single character) -> Allah (shorter)
        string = string.replace('&', ' ');
        string = string.replace('"', ' ');
        string = string.replace('\'', ' ');
        string = string.replace('\u00a0', ' ');  // non-breaking space
        string = string.trim();

        if (string.contains("nickname:")) {
            string = removeNumeralStrings(string);
        }

        return string;
    }

    private static boolean containsIp(String string) {

        // Look for the number of numeral groups and the lengths of those groups to see if this appears to be an IP
        // address.
        String[] split = string.split("[^0-9]");
        int numberOfGroups = 0;
        boolean groupLengthsCorrect = true;
        for (String element : split) {
            if (element.length() > 3) {
                groupLengthsCorrect = false;
            } else if (element.length() > 0) {
                numberOfGroups++;
            }
        }

        return groupLengthsCorrect && numberOfGroups == 4;
    }

    public static String nextId() {

        return "id" + nextId.incrementAndGet();
    }
}
