package com.aryan.spring_security_demo.security.ratelimit;

/**
 * A single client's token bucket. Holds up to {@code capacity} tokens and refills
 * continuously at {@code capacity / refillPeriod} tokens per millisecond; each
 * allowed request spends one token. When the bucket is empty the request is
 * denied and told how long until the next token accrues.
 *
 * <p>Time is passed in (never read from a wall clock inside), so the owning
 * {@link RateLimitService} controls the clock and the bucket stays trivially
 * unit-testable. Access is {@code synchronized} because a single client's
 * concurrent requests may land on different threads and must not race on the
 * token count.
 */
final class TokenBucket {

    private final long capacity;
    private final double refillTokensPerMilli;

    private double availableTokens;
    private long lastRefillMillis;

    TokenBucket(long capacity, long refillPeriodMillis, long nowMillis) {
        this.capacity = capacity;
        this.refillTokensPerMilli = (double) capacity / refillPeriodMillis;
        this.availableTokens = capacity;
        this.lastRefillMillis = nowMillis;
    }

    /** Spends one token if available, otherwise reports how long until one is. */
    synchronized RateLimitService.Decision tryConsume(long nowMillis) {
        refill(nowMillis);
        if (availableTokens >= 1.0d) {
            availableTokens -= 1.0d;
            return RateLimitService.Decision.allow();
        }
        double deficit = 1.0d - availableTokens;
        long retryAfterMillis = (long) Math.ceil(deficit / refillTokensPerMilli);
        return RateLimitService.Decision.deny(retryAfterMillis);
    }

    /**
     * A bucket refilled back to full carries no rate-limit history worth keeping —
     * it is indistinguishable from a freshly created one — so the registry can drop
     * it to bound memory (see {@link RateLimitService#evictReplenishedBuckets()}).
     */
    synchronized boolean isFull(long nowMillis) {
        refill(nowMillis);
        return availableTokens >= capacity;
    }

    private void refill(long nowMillis) {
        if (nowMillis <= lastRefillMillis) {
            return;
        }
        double refilled = (nowMillis - lastRefillMillis) * refillTokensPerMilli;
        availableTokens = Math.min(capacity, availableTokens + refilled);
        lastRefillMillis = nowMillis;
    }
}
