package com.aryan.spring_security_demo.identity.security.jwt;

import com.aryan.spring_security_demo.identity.AuthTokenProperties;
import com.aryan.spring_security_demo.identity.security.user.ShopUserDetails;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Access tokens as JwtUtils makes them and JwtConfig's decoder checks them: signed
 * RS256 by the private key, and accepted only with a published public key, only
 * RS256, only before expiry by the injected clock, and only from this issuer.
 */
class JwtUtilsTest {

    private static final Instant ISSUED = Instant.parse("2026-03-14T12:00:00Z");
    private static final Duration LIFETIME = Duration.ofMinutes(15);

    private final KeyPair pair = RsaKeys.generate();
    private final JwtKeys keys = new JwtKeys(properties(pair));

    @Test
    void aToken_isSignedRs256_andNamesItsKey() {
        Jwt jwt = decoderAt(keys, ISSUED).decode(token());

        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsEntry("kid", keys.keyId())
                .containsEntry("typ", "JWT");
        assertThat(jwt.getSubject()).isEqualTo("ada@example.com");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("shopping-cart-api");
        assertThat(((Number) jwt.getClaim(JwtUtils.ID_CLAIM)).longValue()).isEqualTo(7L);
        assertThat(jwt.getClaimAsStringList(JwtUtils.ROLES_CLAIM)).containsExactly("ROLE_CUSTOMER");
        assertThat(jwt.getExpiresAt()).isEqualTo(ISSUED.plus(LIFETIME));
    }

    @Test
    void aToken_isValidUntilItsExpiry_byTheInjectedClock() {
        String token = token();

        assertThat(decoderAt(keys, ISSUED.plus(LIFETIME).minusSeconds(1)).decode(token).getSubject())
                .isEqualTo("ada@example.com");
        assertThatThrownBy(() -> decoderAt(keys, ISSUED.plus(LIFETIME).plusSeconds(1)).decode(token))
                .isInstanceOf(JwtException.class).hasMessageContaining("expired");
    }

    @Test
    void aTokenSignedWithAnotherKey_isRefused() {
        JwtKeys someoneElses = new JwtKeys(properties(RsaKeys.generate()));
        String forged = jwtUtils(someoneElses, properties(pair), ISSUED).generateTokenFromUserDetails(ada());

        assertThatThrownBy(() -> decoderAt(keys, ISSUED).decode(forged)).isInstanceOf(JwtException.class);
    }

    // The classic attack on RS256: sign with HMAC, using the public key (which is
    // published) as the secret, and hope the verifier follows the token's "alg".
    @Test
    void anHs256TokenKeyedWithThePublicKey_isRefused() throws Exception {
        SignedJWT hmac = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(keys.keyId()).build(), claims());
        hmac.sign(new MACSigner(pair.getPublic().getEncoded()));

        assertThatThrownBy(() -> decoderAt(keys, ISSUED).decode(hmac.serialize())).isInstanceOf(JwtException.class);
    }

    @Test
    void anUnsignedToken_isRefused() {
        String unsigned = new PlainJWT(claims()).serialize();

        assertThatThrownBy(() -> decoderAt(keys, ISSUED).decode(unsigned)).isInstanceOf(JwtException.class);
    }

    @Test
    void aTokenFromAnotherIssuer_isRefused() {
        AuthTokenProperties elsewhere = properties(pair);
        elsewhere.setIssuer("some-other-api");
        String token = jwtUtils(keys, elsewhere, ISSUED).generateTokenFromUserDetails(ada());

        assertThatThrownBy(() -> decoderAt(keys, ISSUED).decode(token))
                .isInstanceOf(JwtException.class).hasMessageContaining("iss");
    }

    // While a key is replaced: tokens signed with the old one stay valid as long as
    // its public half is listed in JWT_PREVIOUS_PUBLIC_KEYS, and not once it's gone.
    @Test
    void aTokenFromThePreviousKey_isValidWhileThatKeyIsListed() {
        String oldToken = token();
        KeyPair next = RsaKeys.generate();
        AuthTokenProperties rotated = properties(next);
        rotated.setPreviousPublicKeys(List.of(RsaKeys.encode(pair.getPublic())));

        assertThat(decoderAt(new JwtKeys(rotated), ISSUED).decode(oldToken).getSubject()).isEqualTo("ada@example.com");
        assertThatThrownBy(() -> decoderAt(new JwtKeys(properties(next)), ISSUED).decode(oldToken))
                .isInstanceOf(JwtException.class);
    }

    private String token() {
        return jwtUtils(keys, properties(pair), ISSUED).generateTokenFromUserDetails(ada());
    }

    private static JwtUtils jwtUtils(JwtKeys keys, AuthTokenProperties properties, Instant now) {
        return new JwtUtils(properties, new JwtConfig().jwtEncoder(keys), keys, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static JwtDecoder decoderAt(JwtKeys keys, Instant now) {
        return new JwtConfig().jwtDecoder(keys, properties(null), Clock.fixed(now, ZoneOffset.UTC));
    }

    private static AuthTokenProperties properties(KeyPair pair) {
        AuthTokenProperties properties = new AuthTokenProperties();
        if (pair != null) {
            properties.setSigningKey(RsaKeys.encode(pair.getPrivate()));
        }
        properties.setExpirationInMils(LIFETIME.toMillis());
        return properties;
    }

    private static JWTClaimsSet claims() {
        return new JWTClaimsSet.Builder().subject("ada@example.com").issuer("shopping-cart-api")
                .claim(JwtUtils.ID_CLAIM, 7L).expirationTime(Date.from(ISSUED.plus(LIFETIME))).build();
    }

    private static ShopUserDetails ada() {
        return new ShopUserDetails(7L, "ada@example.com", "hash", List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }
}
