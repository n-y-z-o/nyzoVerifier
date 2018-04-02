package co.nyzo.verifier;

import co.nyzo.verifier.util.SignatureUtil;
import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.EdDSASecurityProvider;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;

import java.io.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

public class Wallet {

    static {
        Security.addProvider(new EdDSASecurityProvider());
    }

    private EdDSAPrivateKey privateKey;
    private EdDSAPublicKey publicKey;

    public static void main(String[] args) {

        // Make a new wallet.
        Wallet wallet0 = new Wallet();
        System.out.println("(0) private key seed is " +
                ByteUtil.arrayAsStringWithDashes(wallet0.getPrivateKey().getSeed()) + " (" +
                wallet0.getPrivateKey().getSeed().length + ")");
        System.out.println("(0) public key aByte is " +
                ByteUtil.arrayAsStringWithDashes(wallet0.getPublicKey().getAbyte()));

        byte[] seed = wallet0.getPrivateKey().getSeed();
        Wallet wallet1 = Wallet.fromPrivateSeed(seed);
        System.out.println("(1) private key seed is " +
                ByteUtil.arrayAsStringWithDashes(wallet1.getPrivateKey().getSeed()));
        System.out.println("(1) public key aByte is " +
                ByteUtil.arrayAsStringWithDashes(wallet1.getPublicKey().getAbyte()));

        byte[] aByte = wallet1.getPublicKey().getAbyte();
        Wallet wallet2 = Wallet.fromPublicIdentifier(aByte);
        System.out.println("(2) private key is " + wallet2.getPrivateKey());
        System.out.println("(2) public key aByte is " +
                ByteUtil.arrayAsStringWithDashes(wallet2.getPublicKey().getAbyte()));
    }

    private Wallet() { }

    public static Wallet generateNew() {

        Wallet wallet = new Wallet();
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EdDSA", "EdDSA");
            SecureRandom random = SecureRandom.getInstanceStrong();
            EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName("Ed25519");
            keyPairGenerator.initialize(spec, random);
            KeyPair pair = keyPairGenerator.generateKeyPair();

            wallet.privateKey = (EdDSAPrivateKey) pair.getPrivate();
            wallet.publicKey = (EdDSAPublicKey) pair.getPublic();

            System.out.println("private key is " + wallet.privateKey);

            // NOTE: may need to install haveged (sudo apt-get install haveged)

        } catch (Exception e) {
            System.out.println("exception: " + e.getMessage());
            e.printStackTrace();
        }

        return wallet;
    }

    public static Wallet fromPrivateSeed(byte[] seed) {

        Wallet wallet = new Wallet();
        try {
            wallet.privateKey = new EdDSAPrivateKey(new PKCS8EncodedKeySpec(KeyUtil.encodedFromSeed(seed)));

            byte[] encodedAByte = KeyUtil.encodedFromAByte(wallet.privateKey.getAbyte());
            wallet.publicKey = new EdDSAPublicKey(new X509EncodedKeySpec(encodedAByte));

        } catch (Exception e) {
            e.printStackTrace();
        }

        return wallet;
    }

    public static Wallet fromPublicIdentifier(byte[] identifier) {

        Wallet wallet = new Wallet();
        try {
            wallet.privateKey = null;

            byte[] encodedAByte = KeyUtil.encodedFromAByte(identifier);
            wallet.publicKey = new EdDSAPublicKey(new X509EncodedKeySpec(encodedAByte));

        } catch (Exception e) {
            e.printStackTrace();
        }

        return wallet;
    }

    public byte[] sign(byte[] array) {
        return sign(array, array.length);
    }

    public byte[] sign(byte[] array, int length) {

        byte[] signatureBytes = null;
        try {
            Signature signature = new EdDSAEngine(MessageDigest.getInstance(SignatureUtil.spec.getHashAlgorithm()));
            signature.initSign(privateKey);

            signature.update(array, 0, length);
            signatureBytes = signature.sign();
        } catch (Exception ignore) { }

        return signatureBytes;
    }

    public boolean verify(byte[] message) {

        boolean verified = false;
        try {
            Signature signature = new EdDSAEngine(MessageDigest.getInstance(SignatureUtil.spec.getHashAlgorithm()));
            signature.initVerify(publicKey);

            signature.update(ByteUtil.dataSegment(message));
            verified = signature.verify(ByteUtil.signatureSegment(message));
        } catch (Exception ignore) { }

        return verified;
    }

    public EdDSAPrivateKey getPrivateKey() {
        return privateKey;
    }

    public EdDSAPublicKey getPublicKey() {
        return publicKey;
    }

    public byte[] getIdentifier() {
        byte[] result;
        if (publicKey == null) {
            result = new byte[32];
        } else {
            result = Arrays.copyOf(publicKey.getAbyte(), 32);
        }

        return result;
    }

    public void toFile(File file) {

        try {
            file.getParentFile().mkdirs();
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            fileOutputStream.write(this.getPrivateKey().getSeed());
            fileOutputStream.close();
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
    }

    public static Wallet fromFile(File file) {

        // For any file, the last 32 bytes are the private key.
        Wallet wallet = null;
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            byte[] bytes = new byte[32];
            fileInputStream.skip(file.length() - 32);
            fileInputStream.read(bytes);
            wallet = fromPrivateSeed(bytes);
            fileInputStream.close();
        } catch (Exception ignored) { }

        return wallet;
    }
}
