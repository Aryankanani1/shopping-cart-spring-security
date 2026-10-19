package com.aryan.spring_security_demo.identity.security.jwt;

import com.aryan.spring_security_demo.identity.AuthTokenProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The signing key, and the public keys that are published to verify tokens. */
class JwtKeysTest {

    private final KeyPair current = RsaKeys.generate();
    private final KeyPair previous = RsaKeys.generate();

    @Test
    void theKeyId_isTheKeysThumbprint_soTheSameKeyHasTheSameId() throws Exception {
        JwtKeys keys = new JwtKeys(properties(current));

        assertThat(keys.keyId()).isEqualTo(keys.signingKey().computeThumbprint().toString());
        assertThat(new JwtKeys(properties(current)).keyId()).isEqualTo(keys.keyId());
        assertThat(new JwtKeys(properties(previous)).keyId()).isNotEqualTo(keys.keyId());
    }

    @Test
    void thePublishedKeys_holdNothingPrivate() {
        JwtKeys keys = new JwtKeys(properties(current));

        assertThat(keys.signingKey().isPrivate()).isTrue();
        assertThat(keys.publicKeys().getKeys()).allSatisfy(key -> {
            assertThat(key.isPrivate()).isFalse();
            assertThat(key.getKeyUse()).isEqualTo(KeyUse.SIGNATURE);
            assertThat(key.getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        });
        assertThat(keys.publicKeys().toString()).doesNotContain("\"d\"", "\"p\"", "\"q\"");
    }

    @Test
    void previousKeys_arePublishedAfterTheSigningKey_eachOnce() {
        AuthTokenProperties properties = properties(current);
        String old = RsaKeys.encode(previous.getPublic());
        properties.setPreviousPublicKeys(List.of(old, " ", old, RsaKeys.encode(current.getPublic())));

        JwtKeys keys = new JwtKeys(properties);

        List<String> ids = keys.publicKeys().getKeys().stream().map(JWK::getKeyID).toList();
        assertThat(ids).containsExactly(keys.keyId(), new JwtKeys(properties(previous)).keyId());
    }

    @Test
    void noKey_isRefused_unlessAThrowawayOneMayBeMade() {
        AuthTokenProperties none = new AuthTokenProperties();
        assertThatThrownBy(() -> new JwtKeys(none)).hasMessageContaining("JWT_SIGNING_KEY");

        none.setGenerateKeyIfMissing(true);
        JwtKeys first = new JwtKeys(none);
        JwtKeys second = new JwtKeys(none);
        assertThat(first.signingKey().isPrivate()).isTrue();
        assertThat(first.keyId()).isNotEqualTo(second.keyId());
    }

    @Test
    void aConfiguredKey_isUsedEvenWhereAThrowawayOneMayBeMade() {
        AuthTokenProperties properties = properties(current);
        properties.setGenerateKeyIfMissing(true);

        assertThat(new JwtKeys(properties).keyId()).isEqualTo(new JwtKeys(properties(current)).keyId());
    }

    private static AuthTokenProperties properties(KeyPair pair) {
        AuthTokenProperties properties = new AuthTokenProperties();
        properties.setSigningKey(RsaKeys.encode(pair.getPrivate()));
        return properties;
    }
}
