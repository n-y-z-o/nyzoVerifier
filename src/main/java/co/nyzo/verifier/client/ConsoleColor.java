package co.nyzo.verifier.client;

import co.nyzo.verifier.util.PreferencesUtil;

public enum ConsoleColor {

    // These values are shortcuts to deriving the ANSI color values for foreground and background, normal and bright.
    // The Wikipedia article explains it well: https://en.wikipedia.org/wiki/ANSI_escape_code.
    Black(0),
    Red(1),
    Green(2),
    Yellow(3),
    Blue(4),
    Magenta(5),
    Cyan(6),
    White(7);

    private static final String colorEnableKey = "enable_console_color";
    private static final boolean enableColor = PreferencesUtil.getBoolean(colorEnableKey, false);

    public static final String reset = enableColor ? "\u001B[0m" : "";

    private int value;

    ConsoleColor(int value) {
        this.value = value;
    }

    public String background() {
        return enableColor ? "\u001B[" + (value + 40) + "m" : "";
    }

    public String bright() {
        return enableColor ? "\u001B[" + (value + 90) + "m" : "";
    }

    public String backgroundBright() {
        return enableColor ? "\u001B[" + (value + 100) + "m" : "";
    }

    @Override
    public String toString() {
        return enableColor ? "\u001B[" + (value + 30) + "m" : "";
    }
}
