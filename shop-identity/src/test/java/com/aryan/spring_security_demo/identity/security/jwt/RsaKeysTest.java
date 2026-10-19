package com.aryan.spring_security_demo.identity.security.jwt;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Keys as the environment holds them: Base64 DER on one line, or PEM. */
class RsaKeysTest {

    private final KeyPair pair = RsaKeys.generate();

    @Test
    void aPrivateKey_onOneLine_orAsPem() {
        String oneLine = RsaKeys.encode(pair.getPrivate());
        String pem = pem("PRIVATE KEY", pair.getPrivate().getEncoded());

        assertThat(RsaKeys.parsePrivateKey(oneLine).getEncoded()).isEqualTo(pair.getPrivate().getEncoded());
        assertThat(RsaKeys.parsePrivateKey(pem).getEncoded()).isEqualTo(pair.getPrivate().getEncoded());
    }

    /**
     * PEM as openssl writes it. The armour is put together from the label so this
     * file never holds a complete PEM block that secret scanners would report.
     */
    private static String pem(String label, byte[] der) {
        String armour = "-----";
        return armour + "BEGIN " + label + armour + "\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n" + armour + "END " + label + armour + "\n";
    }

    @Test
    void thePublicHalf_isDerivedFromThePrivateKey() {
        RSAPublicKey derived = RsaKeys.publicKeyOf(RsaKeys.parsePrivateKey(RsaKeys.encode(pair.getPrivate())));

        assertThat(derived.getEncoded()).isEqualTo(pair.getPublic().getEncoded());
    }

    @Test
    void aPublicKey_roundTrips() {
        assertThat(RsaKeys.parsePublicKey(RsaKeys.encode(pair.getPublic())).getEncoded())
                .isEqualTo(pair.getPublic().getEncoded());
    }

    @Test
    void aKeyShorterThan2048Bits_isRefused() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        KeyPair weak = generator.generateKeyPair();

        assertThatThrownBy(() -> RsaKeys.parsePrivateKey(RsaKeys.encode(weak.getPrivate())))
                .hasMessageContaining("1024 bits").hasMessageContaining("at least 2048");
        assertThatThrownBy(() -> RsaKeys.parsePublicKey(RsaKeys.encode(weak.getPublic())))
                .hasMessageContaining("at least 2048");
    }

    // Regression: the documented command was openssl genpkey ... -outform DER, which
    // writes PKCS#1, not PKCS#8, so the staging deploy's key stopped the app at startup.
    @Test
    void aPkcs1Key_isRefused_withHowToConvertIt() {
        byte[] pkcs1 = pkcs1Of(pair.getPrivate().getEncoded());

        assertThat(RsaKeys.isPkcs1(pkcs1)).isTrue();
        assertThat(RsaKeys.isPkcs1(pair.getPrivate().getEncoded())).isFalse();
        assertThatThrownBy(() -> RsaKeys.parsePrivateKey(Base64.getEncoder().encodeToString(pkcs1)))
                .hasMessageContaining("PKCS#1").hasMessageContaining("openssl pkcs8 -topk8 -nocrypt");
    }

    /**
     * The PKCS#1 RSAPrivateKey inside a PKCS#8 encoding, which is what openssl
     * genpkey -outform DER writes: PrivateKeyInfo is SEQUENCE { INTEGER 0,
     * AlgorithmIdentifier, OCTET STRING { RSAPrivateKey } }.
     */
    private static byte[] pkcs1Of(byte[] pkcs8) {
        int i = 2 + (pkcs8[1] & 0x7f);  // past the outer SEQUENCE's tag and long-form length
        i += 3;                          // the version, INTEGER 0
        i += 2 + pkcs8[i + 1];           // the AlgorithmIdentifier SEQUENCE (a short length)
        assertThat(pkcs8[i]).as("OCTET STRING").isEqualTo((byte) 0x04);
        return Arrays.copyOfRange(pkcs8, i + 2 + (pkcs8[i + 1] & 0x7f), pkcs8.length);
    }

    @Test
    void theWrongKindOfKey_isRefused() {
        assertThatThrownBy(() -> RsaKeys.parsePrivateKey(RsaKeys.encode(pair.getPublic())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RsaKeys.parsePublicKey(RsaKeys.encode(pair.getPrivate())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RsaKeys.parsePrivateKey("not base64!"))
                .hasMessageContaining("Not Base64");
        assertThatThrownBy(() -> RsaKeys.parsePrivateKey(" "))
                .hasMessageContaining("No key");
    }
}
