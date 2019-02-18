package co.nyzo.verifier.client;

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

    public static final String reset = "\u001B[0m";

    private int value;

    ConsoleColor(int value) {
        this.value = value;
    }

    public String background() {
        return "\u001B[" + (value + 40) + "m";
    }

    public String bright() {
        return "\u001B[" + (value + 90) + "m";
    }

    public String backgroundBright() {
        return "\u001B[" + (value + 100) + "m";
    }

    @Override
    public String toString() {
        return "\u001B[" + (value + 30) + "m";
    }
}
