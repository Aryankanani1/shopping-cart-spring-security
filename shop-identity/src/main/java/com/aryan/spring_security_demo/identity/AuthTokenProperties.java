package com.aryan.spring_security_demo.identity;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe, validated JWT settings ({@code auth.token.*}), replacing the
 * scattered {@code @Value} lookups in {@code JwtUtils}.
 *
 * <p>The secret has no default — it must be supplied per environment (via the
 * {@code JWT_SECRET} env var). A missing secret, or one that can't be used as an
 * HS256 key, fails the application at startup instead of at first login.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "auth.token")
public class AuthTokenProperties {

    /** Base64-encoded HMAC secret; must be >= 256 bits (32 bytes) for HS256. */
    @NotBlank
    private String jwtSecret;

    /**
     * Builds the key the same way {@code JwtUtils} does, so a secret that passes
     * here signs tokens. Without this check, a short or non-Base64 secret started
     * fine and then failed every login with a 401 "Invalid or expired token".
     */
    @AssertTrue(message = "JWT_SECRET must be a Base64-encoded key of at least 256 bits "
            + "(generate one with: openssl rand -base64 32)")
    public boolean isJwtSecretUsable() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            return true;  // @NotBlank reports a missing secret
        }
        try {
            Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    /**
     * Access-token lifetime in milliseconds. Kept short (default 15 min) so a
     * leaked token's exposure window is small; clients keep sessions alive by
     * exchanging a refresh token rather than by holding a long-lived access token.
     */
    @Positive
    private long expirationInMils = 900_000L;

    /**
     * Refresh-token lifetime in milliseconds (default 7 days). Longer-lived than
     * the access token but revocable server-side (see {@code RefreshTokenService}),
     * so control is retained despite the length.
     */
    @Positive
    private long refreshExpirationInMils = 604_800_000L;

    /**
     * Cron expression for the scheduled purge of expired refresh tokens (see
     * {@code RefreshTokenCleanupService}). Rotation leaves a spent row behind on
     * every refresh, so they are swept at a fixed wall-clock time (default daily
     * at 03:00, an off-peak window). Overlap across instances is prevented by
     * ShedLock, not by the schedule itself. Default: {@code 0 0 3 * * *}.
     */
    @NotBlank
    private String cleanupCron = "0 0 3 * * *";
}
