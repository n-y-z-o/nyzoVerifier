package co.nyzo.verifier;

import co.nyzo.verifier.util.SignatureUtil;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.EdDSASecurityProvider;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

public class KeyUtil {

    static {
        Security.addProvider(new EdDSASecurityProvider());
    }

    public static void main(String[] args) throws Exception {

        // Create a key and sign some data.
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EdDSA", "EdDSA");
        SecureRandom random = SecureRandom.getInstanceStrong();
        EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName("Ed25519");
        keyPairGenerator.initialize(spec, random);
        KeyPair pair = keyPairGenerator.generateKeyPair();
        EdDSAPrivateKey privateKey = (EdDSAPrivateKey) pair.getPrivate();
        EdDSAPublicKey publicKey = (EdDSAPublicKey) pair.getPublic();

        System.out.println("original private key:    " + ByteUtil.arrayAsStringWithDashes(privateKey.getSeed()));
        System.out.println("original public key:     " + ByteUtil.arrayAsStringWithDashes(publicKey.getAbyte()));

        byte[] bytesToSign = "hello".getBytes(StandardCharsets.US_ASCII);
        byte[] signature = SignatureUtil.signBytes(bytesToSign, privateKey.getSeed());
        System.out.println("original signature:      " + ByteUtil.arrayAsStringWithDashes(signature));

        // Sign the same data using the SignatureUtil class and verify the original data.
        byte[] signature2 = SignatureUtil.signBytes(bytesToSign, privateKey.getSeed());
        System.out.println("SignatureUtil signature: " + ByteUtil.arrayAsStringWithDashes(signature2));

        boolean signatureIsValid = SignatureUtil.signatureIsValid(signature, bytesToSign, publicKey.getAbyte());
        System.out.println("signature is valid:      " + signatureIsValid);

        boolean signature2IsValid = SignatureUtil.signatureIsValid(signature2, bytesToSign, publicKey.getAbyte());
        System.out.println("signature 2 is valid:    " + signature2IsValid);
    }

    // The encodedFromSeed and encodedFromAByte methods in this class are based on copy/paste from the EdDSAPrivateKey
    // class of the EdDSA library.

    private static final int OID_ED25519 = 112;

    public static byte[] encodedFromSeed(byte[] seed) {

        int totlen = 16 + seed.length;
        byte[] rv = new byte[totlen];
        int idx = 0;
        // sequence
        rv[idx++] = 0x30;
        rv[idx++] = (byte) (totlen - 2);
        // version
        rv[idx++] = 0x02;
        rv[idx++] = 1;
        // v1 - no public key included
        rv[idx++] = 0;
        // Algorithm Identifier
        // sequence
        rv[idx++] = 0x30;
        rv[idx++] = 5;
        // OID
        // https://msdn.microsoft.com/en-us/library/windows/desktop/bb540809%28v=vs.85%29.aspx
        rv[idx++] = 0x06;
        rv[idx++] = 3;
        rv[idx++] = (1 * 40) + 3;
        rv[idx++] = 101;
        rv[idx++] = (byte) OID_ED25519;
        // params - absent
        // PrivateKey
        rv[idx++] = 0x04;  // octet string
        rv[idx++] = (byte) (2 + seed.length);
        // CurvePrivateKey
        rv[idx++] = 0x04;  // octet string
        rv[idx++] = (byte) seed.length;
        // the key
        System.arraycopy(seed, 0, rv, idx, seed.length);

        return rv;
    }

    public static byte[] encodedFromAByte(byte[] aByte) {

        int totlen = 12 + aByte.length;
        byte[] rv = new byte[totlen];
        int idx = 0;
        // sequence
        rv[idx++] = 0x30;
        rv[idx++] = (byte) (totlen - 2);
        // Algorithm Identifier
        // sequence
        rv[idx++] = 0x30;
        rv[idx++] = 5;
        // OID
        // https://msdn.microsoft.com/en-us/library/windows/desktop/bb540809%28v=vs.85%29.aspx
        rv[idx++] = 0x06;
        rv[idx++] = 3;
        rv[idx++] = (1 * 40) + 3;
        rv[idx++] = 101;
        rv[idx++] = (byte) OID_ED25519;
        // params - absent
        // the key
        rv[idx++] = 0x03; // bit string
        rv[idx++] = (byte) (1 + aByte.length);
        rv[idx++] = 0; // number of trailing unused bits
        System.arraycopy(aByte, 0, rv, idx, aByte.length);
        return rv;
    }

    public static PrivateKey privateKeyFromSeed(byte[] seed) {

        PrivateKey key = null;

        try {
            byte[] encodedSeed = KeyUtil.encodedFromSeed(seed);
            key = new EdDSAPrivateKey(new PKCS8EncodedKeySpec(encodedSeed));
        } catch (Exception ignored) { }

        return key;
    }

    public static PublicKey publicKeyFromIdentifier(byte[] identifier) {

        PublicKey key = null;

        try {
            byte[] encodedAByte = KeyUtil.encodedFromAByte(identifier);
            key = new EdDSAPublicKey(new X509EncodedKeySpec(encodedAByte));
        } catch (Exception ignored) { }

        return key;
    }

    public static byte[] identifierForSeed(byte[] seed) {

        byte[] identifier = new byte[32];
        try {
            EdDSAPrivateKey privateKey = new EdDSAPrivateKey(new PKCS8EncodedKeySpec(KeyUtil.encodedFromSeed(seed)));
            identifier = Arrays.copyOf(KeyUtil.encodedFromAByte(privateKey.getAbyte()), FieldByteSize.identifier);
        } catch (Exception ignored) { }

        return identifier;
    }
}
