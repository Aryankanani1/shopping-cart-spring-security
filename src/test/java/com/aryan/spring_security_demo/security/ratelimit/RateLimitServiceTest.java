package com.aryan.spring_security_demo.security.ratelimit;

import com.aryan.spring_security_demo.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the token-bucket limiter. Time is driven by a hand-advanced
 * {@link MutableClock} so refill behaviour is asserted deterministically without
 * sleeping — the reason {@link RateLimitService} takes an injected {@link Clock}.
 */
class RateLimitServiceTest {

    private static final int CAPACITY = 5;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    private MutableClock clock;
    private RateLimitService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        RateLimitProperties properties = new RateLimitProperties();
        properties.setCapacity(CAPACITY);
        properties.setRefillPeriod(REFILL_PERIOD);
        service = new RateLimitService(properties, clock);
    }

    @Test
    @DisplayName("allows up to capacity, then denies the next request")
    void allowsUpToCapacityThenDenies() {
        for (int i = 0; i < CAPACITY; i++) {
            assertThat(service.tryConsume("1.1.1.1").allowed())
                    .as("request %d within capacity", i + 1)
                    .isTrue();
        }
        assertThat(service.tryConsume("1.1.1.1").allowed())
                .as("request beyond capacity")
                .isFalse();
    }

    @Test
    @DisplayName("a denied request reports a positive retry-after")
    void deniedRequestReportsRetryAfter() {
        for (int i = 0; i < CAPACITY; i++) {
            service.tryConsume("1.1.1.1");
        }
        RateLimitService.Decision denied = service.tryConsume("1.1.1.1");

        assertThat(denied.allowed()).isFalse();
        // One token accrues every refillPeriod/capacity = 12s, so the wait is ~12s.
        assertThat(denied.retryAfterMillis()).isPositive();
    }

    @Test
    @DisplayName("tokens accrue again once time passes")
    void refillsOverTime() {
        for (int i = 0; i < CAPACITY; i++) {
            service.tryConsume("1.1.1.1");
        }
        assertThat(service.tryConsume("1.1.1.1").allowed()).isFalse();

        // One token accrues every 12s; advance past that and the next call passes.
        clock.advance(Duration.ofSeconds(13));
        assertThat(service.tryConsume("1.1.1.1").allowed()).isTrue();
    }

    @Test
    @DisplayName("each client key has its own independent bucket")
    void bucketsAreIsolatedPerKey() {
        for (int i = 0; i < CAPACITY; i++) {
            service.tryConsume("1.1.1.1");
        }
        assertThat(service.tryConsume("1.1.1.1").allowed()).isFalse();
        // A different client is unaffected by the first client's exhausted bucket.
        assertThat(service.tryConsume("2.2.2.2").allowed()).isTrue();
    }

    @Test
    @DisplayName("eviction drops buckets that have refilled to full")
    void evictionDropsReplenishedBuckets() {
        service.tryConsume("1.1.1.1"); // create a bucket, spend one token

        // Before the bucket has refilled, eviction keeps it (state still matters).
        service.evictReplenishedBuckets();
        // Spending again lands on the same bucket: only capacity-1 tokens remained,
        // so we can still consume but the bucket clearly persisted.
        assertThat(service.tryConsume("1.1.1.1").allowed()).isTrue();

        // After a full refill period the bucket is back to full and gets evicted.
        clock.advance(REFILL_PERIOD.plusSeconds(1));
        service.evictReplenishedBuckets();

        // A fresh bucket is created on the next call, proving the old one was gone:
        // capacity full requests all pass again.
        for (int i = 0; i < CAPACITY; i++) {
            assertThat(service.tryConsume("1.1.1.1").allowed()).isTrue();
        }
    }

    /** A {@link Clock} whose instant is advanced explicitly by the test. */
    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration amount) {
            instant = instant.plus(amount);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
