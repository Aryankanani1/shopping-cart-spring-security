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

    /** Token lifetime in milliseconds. */
    @Positive
    private long expirationInMils = 3_600_000L;
}
