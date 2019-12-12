package co.nyzo.verifier.client;

public class CommandOutputConsole implements CommandOutput {

    @Override
    public void print(String string) {
        System.out.print(string);
    }

    @Override
    public void println(String string) {
        System.out.println(string);
    }
}
