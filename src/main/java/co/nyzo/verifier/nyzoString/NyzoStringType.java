package co.nyzo.verifier.nyzoString;

public enum NyzoStringType {

    PrefilledData("pre_"),
    PrivateSeed("key_"),
    PublicIdentifier("id__"),
    Micropay("pay_"),
    Transaction("tx__");

    private String prefix;
    private byte[] prefixBytes;
    private NyzoString data;

    NyzoStringType(String prefix) {
        this.prefix = prefix;
        this.prefixBytes = NyzoStringEncoder.byteArrayForEncodedString(prefix);
    }

    public String getPrefix() {
        return prefix;
    }

    public byte[] getPrefixBytes() {
        return prefixBytes;
    }

    public static NyzoStringType forPrefix(String prefix) {

        NyzoStringType result = null;
        for (NyzoStringType type : values()) {
            if (type.getPrefix().equals(prefix)) {
                result = type;
            }
        }

        return result;
    }
}
