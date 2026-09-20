package com.aryan.spring_security_demo.security.ratelimit;

import com.aryan.spring_security_demo.config.RateLimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory, per-client token-bucket rate limiter. Keeps one {@link TokenBucket}
 * per client key in a {@link ConcurrentHashMap} and hands out one token per
 * request via {@link #tryConsume(String)}.
 *
 * <p>Deliberately dependency-free — the same choice as {@code CacheConfig}'s
 * in-memory {@code ConcurrentMapCacheManager}: correct and self-contained for a
 * single instance. Behind a load balancer the per-instance buckets would need a
 * shared backend (Redis, or Bucket4j on a distributed store); the call site here
 * would not change.
 *
 * <p>Time comes from the injected {@link Clock} (like {@code RefreshTokenService})
 * so throttling is deterministic in tests. A scheduled sweep drops replenished
 * buckets so the map can't grow without bound under many distinct client IPs.
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final RateLimitProperties properties;
    private final Clock clock;

    private final ConcurrentMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitService(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Attempts to spend one token for the given client key. */
    public Decision tryConsume(String clientKey) {
        long now = clock.millis();
        TokenBucket bucket = buckets.computeIfAbsent(clientKey, key -> new TokenBucket(
                properties.getCapacity(),
                properties.getRefillPeriod().toMillis(),
                now));
        return bucket.tryConsume(now);
    }

    /**
     * Periodically drops buckets that have refilled to full: an idle client's
     * bucket is identical to a brand-new one, so evicting it changes no behaviour
     * and simply bounds memory.
     *
     * <p>Unlike {@code RefreshTokenCleanupService} this is a <em>non-critical</em>
     * sweep — it only prunes an in-memory {@link ConcurrentHashMap} whose own
     * operations are thread-safe, and it is idempotent (running it twice removes
     * the same full buckets), so overlap is harmless. It therefore uses a plain
     * <strong>fixed rate</strong> (no {@code synchronized} guard needed). Interval:
     * {@code app.ratelimit.eviction-interval-ms} (default hourly); scheduling is
     * enabled by {@code SchedulingConfig}.
     */
    @Scheduled(fixedRateString = "${app.ratelimit.eviction-interval-ms:3600000}", initialDelay = 3_600_000)
    public void evictReplenishedBuckets() {
        long now = clock.millis();
        int before = buckets.size();
        buckets.values().removeIf(bucket -> bucket.isFull(now));
        int evicted = before - buckets.size();
        if (evicted > 0) {
            log.debug("Evicted {} replenished rate-limit bucket(s)", evicted);
        }
    }

    /** Outcome of an attempt to spend a token, with how long to wait when denied. */
    public record Decision(boolean allowed, long retryAfterMillis) {

        static Decision allow() {
            return new Decision(true, 0L);
        }

        static Decision deny(long retryAfterMillis) {
            return new Decision(false, retryAfterMillis);
        }
    }
}
