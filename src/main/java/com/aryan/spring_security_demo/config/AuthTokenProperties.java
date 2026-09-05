package com.aryan.spring_security_demo.config;

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
 * {@code JWT_SECRET} env var). {@code @NotBlank} makes a missing secret fail the
 * application at startup instead of at first login.
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
}
