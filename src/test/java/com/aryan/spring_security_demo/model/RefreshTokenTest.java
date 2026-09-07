package com.aryan.spring_security_demo.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test for the token's expiry logic. No Spring, no mocks, no clock —
 * because {@link RefreshToken#isExpired(Instant)} takes time as a parameter, the
 * expiry boundary is a plain function of state and can be asserted exactly. This
 * is what "test behaviour, not implementation" buys once time is an input rather
 * than a hidden {@code Instant.now()} call.
 */
class RefreshTokenTest {

    private static final Instant EXPIRY = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    @DisplayName("not expired one second before, expired one second after the boundary")
    void isExpired_boundary() {
        RefreshToken token = tokenExpiringAt(EXPIRY);

        assertThat(token.isExpired(EXPIRY.minusSeconds(1))).as("before expiry").isFalse();
        assertThat(token.isExpired(EXPIRY.plusSeconds(1))).as("after expiry").isTrue();
    }

    @Test
    @DisplayName("exactly at the expiry instant the token is still valid (expiry is exclusive)")
    void isExpired_atExactInstant_isNotExpired() {
        RefreshToken token = tokenExpiringAt(EXPIRY);

        assertThat(token.isExpired(EXPIRY)).isFalse();
    }

    @Test
    @DisplayName("isActive is false once revoked, even before expiry")
    void isActive_revokedToken_isInactive() {
        RefreshToken token = tokenExpiringAt(EXPIRY);
        token.setRevoked(true);

        assertThat(token.isActive(EXPIRY.minusSeconds(1)))
                .as("revoked but unexpired is still inactive").isFalse();
    }

    private RefreshToken tokenExpiringAt(Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setExpiresAt(expiresAt);
        return token;
    }
}
