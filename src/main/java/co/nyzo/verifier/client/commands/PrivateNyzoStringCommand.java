package co.nyzo.verifier.client.commands;

import co.nyzo.verifier.ByteUtil;
import co.nyzo.verifier.FieldByteSize;
import co.nyzo.verifier.KeyUtil;
import co.nyzo.verifier.client.ConsoleColor;
import co.nyzo.verifier.client.ConsoleUtil;
import co.nyzo.verifier.client.ValidationResult;
import co.nyzo.verifier.nyzoString.NyzoStringEncoder;
import co.nyzo.verifier.nyzoString.NyzoStringPrivateSeed;
import co.nyzo.verifier.nyzoString.NyzoStringPublicIdentifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PrivateNyzoStringCommand implements Command {

    @Override
    public String getShortCommand() {
        return "NSS";
    }

    @Override
    public String getLongCommand() {
        return "seedString";
    }

    @Override
    public String getDescription() {
        return "create Nyzo strings for a private seed";
    }

    @Override
    public String[] getArgumentNames() {
        return new String[] { "private key" };
    }

    @Override
    public boolean requiresValidation() {
        return false;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ValidationResult validate(List<String> argumentValues) {
        return null;
    }

    @Override
    public void run(List<String> argumentValues) {

        byte[] privateSeed = ByteUtil.byteArrayFromHexString(argumentValues.get(0), FieldByteSize.seed);
        NyzoStringPrivateSeed privateSeedString = new NyzoStringPrivateSeed(privateSeed);

        byte[] publicIdentifier = KeyUtil.identifierForSeed(privateSeed);
        NyzoStringPublicIdentifier publicIdentifierString = new NyzoStringPublicIdentifier(publicIdentifier);

        List<String> labels = Arrays.asList("private seed (raw)", "private seed (Nyzo string)", "public ID (raw)",
                "public ID (Nyzo string)");
        List<String> values = Arrays.asList(ByteUtil.arrayAsStringWithDashes(privateSeed),
                NyzoStringEncoder.encode(privateSeedString), ByteUtil.arrayAsStringWithDashes(publicIdentifier),
                NyzoStringEncoder.encode(publicIdentifierString));

        ConsoleUtil.printTable(Arrays.asList(labels, values));
    }

    public static void printHexWarning() {
        PrivateNyzoStringCommand command = new PrivateNyzoStringCommand();
        System.out.println(ConsoleColor.Yellow.background() + "You appear to be using a raw hexadecimal " +
                "private key. Please convert this to a Nyzo string with the \"" + command.getLongCommand() +
                "\" (" + command.getShortCommand() + ") command." + ConsoleColor.reset);
    }
}
