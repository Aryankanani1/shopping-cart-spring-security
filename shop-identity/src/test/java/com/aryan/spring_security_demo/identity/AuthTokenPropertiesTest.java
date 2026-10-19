package com.aryan.spring_security_demo.identity;

import com.aryan.spring_security_demo.identity.security.jwt.RsaKeys;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The access-token keys are checked when the app starts, so a missing or unusable
 * key stops it with a message that says what's wrong, instead of every login
 * failing later.
 */
class AuthTokenPropertiesTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(Properties.class);

    @Test
    void anRsaKeyOf2048Bits_starts() {
        context.withPropertyValues("auth.token.signingKey=" + RsaKeys.encode(RsaKeys.generate().getPrivate()))
                .run(started -> assertThat(started).hasNotFailed());
    }

    @Test
    void noKey_stopsTheAppAndSaysHowToMakeOne() {
        context.run(started -> assertThat(started).hasFailed()
                .getFailure().rootCause().hasMessageContaining("JWT_SIGNING_KEY must be set")
                .hasMessageContaining("openssl genpkey"));
    }

    @Test
    void noKey_startsWhereAThrowawayOneMayBeMade() {
        context.withPropertyValues("auth.token.generate-key-if-missing=true")
                .run(started -> assertThat(started).hasNotFailed());
    }

    @Test
    void anUnusableSigningKey_stopsTheApp() throws Exception {
        String tooShort = RsaKeys.encode(rsaKeyOf(1024));
        String notRsa = RsaKeys.encode(ecKey());
        String publicNotPrivate = RsaKeys.encode(RsaKeys.generate().getPublic());
        String randomBytes = Base64.getEncoder().encodeToString(new byte[256]);
        for (String key : new String[] {"secret", "%%%%not-base64%%%%", randomBytes, tooShort, notRsa, publicNotPrivate}) {
            context.withPropertyValues("auth.token.signingKey=" + key)
                    .run(started -> assertThat(started).as(key).hasFailed()
                            .getFailure().rootCause().hasMessageContaining("must be an RSA private key"));
        }
    }

    @Test
    void anUnusablePreviousKey_stopsTheApp() {
        context.withPropertyValues(
                        "auth.token.signingKey=" + RsaKeys.encode(RsaKeys.generate().getPrivate()),
                        "auth.token.previousPublicKeys=" + RsaKeys.encode(RsaKeys.generate().getPrivate()))
                .run(started -> assertThat(started).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("JWT_PREVIOUS_PUBLIC_KEYS"));
    }

    @Test
    void previousKeys_areAListSeparatedByCommas() {
        String first = RsaKeys.encode(RsaKeys.generate().getPublic());
        String second = RsaKeys.encode(RsaKeys.generate().getPublic());
        context.withPropertyValues(
                        "auth.token.signingKey=" + RsaKeys.encode(RsaKeys.generate().getPrivate()),
                        "auth.token.previousPublicKeys=" + first + "," + second)
                .run(started -> assertThat(started.getBean(AuthTokenProperties.class).getPreviousPublicKeys())
                        .containsExactly(first, second));
    }

    private static PrivateKey rsaKeyOf(int bits) throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair().getPrivate();
    }

    private static PrivateKey ecKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair().getPrivate();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthTokenProperties.class)
    static class Properties {
    }
}
