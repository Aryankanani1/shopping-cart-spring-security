package com.aryan.spring_security_demo.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Type-safe, validated rate-limiting settings ({@code app.ratelimit.*}) for the
 * unauthenticated auth endpoints, bound the same way as {@code AuthTokenProperties}
 * and {@code StartupProperties} rather than scattered {@code @Value} lookups —
 * invalid config (non-positive capacity, missing period) fails fast at startup.
 *
 * <p>The limiter is a token bucket: {@link #capacity} tokens are available for a
 * burst, and the bucket refills back to full over {@link #refillPeriod}. Defaults
 * (5 requests, refilling over 1 minute) are deliberately tight because the only
 * paths guarded are login/refresh/logout, where a legitimate client makes a
 * handful of calls and an attacker makes thousands.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.ratelimit")
public class RateLimitProperties {

    /**
     * Master switch. On by default; turned off in the {@code test} profile so the
     * shared limiter state can't make unrelated integration tests flaky.
     */
    private boolean enabled = true;

    /** Bucket capacity — the maximum burst of requests before throttling kicks in. */
    @Positive
    private int capacity = 5;

    /** Time for an empty bucket to refill to full capacity (Spring Duration, e.g. {@code 1m}). */
    @NotNull
    private Duration refillPeriod = Duration.ofMinutes(1);
}
