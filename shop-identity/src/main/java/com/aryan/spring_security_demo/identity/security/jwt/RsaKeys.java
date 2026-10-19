package com.aryan.spring_security_demo.identity.security.jwt;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Reads the RSA keys the access tokens are signed and checked with, as they come
 * from the environment: the DER encoding in Base64, either on one line
 * ({@code openssl genpkey ... | openssl pkcs8 -topk8 -nocrypt -outform DER | base64})
 * or as PEM, whose header, footer and line breaks are ignored. A private key is
 * PKCS#8, a public one X.509 SubjectPublicKeyInfo.
 */
public final class RsaKeys {

    /** Smaller RSA keys are no longer considered safe (NIST SP 800-131A). */
    public static final int MIN_BITS = 2048;

    private RsaKeys() {
    }

    /**
     * The private key, which must be RSA and at least {@link #MIN_BITS} long.
     *
     * @throws IllegalArgumentException if it isn't Base64, isn't an RSA private key, or is too short
     */
    public static RSAPrivateCrtKey parsePrivateKey(String encoded) {
        byte[] der = der(encoded);
        try {
            var key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
            if (!(key instanceof RSAPrivateCrtKey rsa)) {
                throw new IllegalArgumentException("Not an RSA private key with its public part (CRT form)");
            }
            requireLongEnough(rsa.getModulus().bitLength());
            return rsa;
        } catch (GeneralSecurityException e) {
            if (isPkcs1(der)) {
                throw new IllegalArgumentException("A PKCS#1 RSA key (as genrsa, or genpkey with -outform DER, "
                        + "writes): convert it to PKCS#8 with openssl pkcs8 -topk8 -nocrypt", e);
            }
            throw new IllegalArgumentException("Not a PKCS#8 RSA private key", e);
        }
    }

    /**
     * Whether {@code der} looks like a PKCS#1 RSAPrivateKey: a SEQUENCE whose
     * version 0 is followed by an INTEGER (the modulus). In PKCS#8 the version is
     * followed by the algorithm's SEQUENCE instead.
     */
    static boolean isPkcs1(byte[] der) {
        if (der.length < 8 || der[0] != 0x30) {
            return false;
        }
        int lengthBytes = (der[1] & 0x80) == 0 ? 0 : der[1] & 0x7f;
        int version = 2 + lengthBytes;
        return der.length > version + 3 && der[version] == 0x02 && der[version + 1] == 0x01
                && der[version + 2] == 0x00 && der[version + 3] == 0x02;
    }

    /**
     * A public key, which must be RSA and at least {@link #MIN_BITS} long.
     *
     * @throws IllegalArgumentException if it isn't Base64, isn't an RSA public key, or is too short
     */
    public static RSAPublicKey parsePublicKey(String encoded) {
        try {
            var key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der(encoded)));
            if (!(key instanceof RSAPublicKey rsa)) {
                throw new IllegalArgumentException("Not an RSA public key");
            }
            requireLongEnough(rsa.getModulus().bitLength());
            return rsa;
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Not an X.509 RSA public key", e);
        }
    }

    /** The public half of a private key in CRT form, which carries the modulus and public exponent. */
    public static RSAPublicKey publicKeyOf(RSAPrivateCrtKey privateKey) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA is unavailable", e);
        }
    }

    /** A new key pair of {@link #MIN_BITS} bits. */
    public static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(MIN_BITS);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA is unavailable", e);
        }
    }

    /** {@code key}'s DER encoding in Base64, on one line: the form the environment variables take. */
    public static String encode(Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    private static byte[] der(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("No key");
        }
        String base64 = encoded.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not Base64", e);
        }
    }

    private static void requireLongEnough(int bits) {
        if (bits < MIN_BITS) {
            throw new IllegalArgumentException("An RSA key of " + bits + " bits; at least " + MIN_BITS + " are needed");
        }
    }
}
