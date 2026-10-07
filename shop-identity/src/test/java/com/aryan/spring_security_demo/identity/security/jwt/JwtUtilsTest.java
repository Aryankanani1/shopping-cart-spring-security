package com.aryan.spring_security_demo.identity.security.jwt;

import com.aryan.spring_security_demo.identity.AuthTokenProperties;
import com.aryan.spring_security_demo.identity.security.user.UserDetails;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Access-token expiry runs on the injected clock, both when minting and when checking. */
class JwtUtilsTest {

    private static final Instant ISSUED = Instant.parse("2026-03-14T12:00:00Z");
    private static final Duration LIFETIME = Duration.ofMinutes(15);

    @Test
    void tokenIsValidUntilItsExpiry_byTheInjectedClock() {
        String token = jwtUtilsAt(ISSUED).generateTokenFromUserDetails(principal());

        assertThat(jwtUtilsAt(ISSUED.plus(LIFETIME).minusSeconds(1)).validateToken(token)).isTrue();
        assertThatThrownBy(() -> jwtUtilsAt(ISSUED.plus(LIFETIME).plusSeconds(1)).validateToken(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void subjectIsTheEmail() {
        JwtUtils jwtUtils = jwtUtilsAt(ISSUED);
        String token = jwtUtils.generateTokenFromUserDetails(principal());

        assertThat(jwtUtils.getUserNameFromToken(token)).isEqualTo("ada@example.com");
    }

    private static JwtUtils jwtUtilsAt(Instant now) {
        AuthTokenProperties properties = new AuthTokenProperties();
        properties.setJwtSecret("CEN/4/BWJAn9lZR7HK2RXAN+ejVVU7p4oBUm7RWtt5Q=");
        properties.setExpirationInMils(LIFETIME.toMillis());
        return new JwtUtils(properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static UserDetails principal() {
        return new UserDetails(1L, "ada@example.com", "hash",
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }
}
