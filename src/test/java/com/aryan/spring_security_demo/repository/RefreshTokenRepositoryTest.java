package com.aryan.spring_security_demo.repository;

import com.aryan.spring_security_demo.model.RefreshToken;
import com.aryan.spring_security_demo.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice test for the {@code refresh_tokens} cleanup query. {@link DataJpaTest}
 * loads only the JPA layer (repositories + entities + a transactional context),
 * not the web/security stack, and runs against the H2 datasource from the
 * {@code test} profile. Each test rolls back, so rows never leak between them.
 *
 * <p>Focus: {@link RefreshTokenRepository#deleteAllExpiredBefore} must drop only
 * tokens past their expiry — including revoked ones — while leaving every
 * unexpired token in place (a revoked-but-unexpired token is kept so a replay of
 * it can still be caught as reuse).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RefreshTokenRepositoryTest {

    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private TestEntityManager em;

    private User user;

    @BeforeEach
    void setUp() {
        User u = new User();
        u.setFirstName("Ada");
        u.setLastName("Lovelace");
        u.setEmail("shopper@example.com");
        u.setPassword("irrelevant");
        user = em.persist(u);
    }

    @Test
    @DisplayName("deleteAllExpiredBefore removes expired tokens (revoked or not) and keeps unexpired ones")
    void deleteAllExpiredBefore_purgesOnlyExpired() {
        Instant now = Instant.now();
        persistToken("expired-active", now.minus(1, ChronoUnit.DAYS), false);
        persistToken("expired-revoked", now.minus(1, ChronoUnit.DAYS), true);
        persistToken("live-active", now.plus(1, ChronoUnit.DAYS), false);
        persistToken("live-revoked", now.plus(1, ChronoUnit.DAYS), true);
        em.flush();

        int deleted = refreshTokenRepository.deleteAllExpiredBefore(now);
        em.clear(); // bulk delete bypasses the persistence context — read fresh from the DB

        assertThat(deleted).as("two expired rows removed").isEqualTo(2);
        assertThat(refreshTokenRepository.findByTokenHash("expired-active")).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash("expired-revoked")).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash("live-active")).isPresent();
        assertThat(refreshTokenRepository.findByTokenHash("live-revoked"))
                .as("revoked but unexpired token is kept for reuse detection").isPresent();
    }

    @Test
    @DisplayName("deleteAllExpiredBefore is a no-op (returns 0) when nothing has expired")
    void deleteAllExpiredBefore_noExpiredTokens_returnsZero() {
        Instant now = Instant.now();
        persistToken("live", now.plus(1, ChronoUnit.DAYS), false);
        em.flush();

        int deleted = refreshTokenRepository.deleteAllExpiredBefore(now);

        assertThat(deleted).isZero();
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
    }

    private void persistToken(String hash, Instant expiresAt, boolean revoked) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash);
        token.setExpiresAt(expiresAt);
        token.setRevoked(revoked);
        em.persist(token);
    }
}
