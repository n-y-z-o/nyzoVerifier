package co.nyzo.verifier.util;

import co.nyzo.verifier.KeyUtil;
import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSASecurityProvider;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;

import java.security.*;

public class SignatureUtil {

    public static final EdDSAParameterSpec spec;

    static {
        Security.addProvider(new EdDSASecurityProvider());
        spec = EdDSANamedCurveTable.getByName("Ed25519");
    }

    public static byte[] signBytes(byte[] bytesToSign, byte[] privateSeed) {

        byte[] signatureBytes = null;

        try {
            Signature signature = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));
            PrivateKey privateKey = KeyUtil.privateKeyFromSeed(privateSeed);
            signature.initSign(privateKey);

            signature.update(bytesToSign);
            signatureBytes = signature.sign();

        } catch (Exception ignored) { }

        return signatureBytes;
    }

    public static boolean signatureIsValid(byte[] signatureBytes, byte[] signedBytes, byte[] publicIdentifier) {

        boolean signatureIsValid;

        try {
            Signature signature = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));
            PublicKey publicKey = KeyUtil.publicKeyFromIdentifier(publicIdentifier);
            signature.initVerify(publicKey);

            signature.update(signedBytes);
            signatureIsValid = signature.verify(signatureBytes);

        } catch (Exception ignored) {

            signatureIsValid = false;
        }

        return signatureIsValid;
    }
}
