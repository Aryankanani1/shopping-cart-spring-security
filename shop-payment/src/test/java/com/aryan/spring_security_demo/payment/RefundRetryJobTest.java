package com.aryan.spring_security_demo.payment;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RefundRetryJobTest {

    // A refund younger than the grace period is left to the after-commit attempt.
    @Test
    void run_retriesTheRefundsOwedSinceBeforeTheGracePeriod() {
        RefundProcessor processor = mock(RefundProcessor.class);
        Instant now = Instant.parse("2026-10-16T09:00:00Z");

        new RefundRetryJob(processor, Clock.fixed(now, ZoneOffset.UTC)).run();

        verify(processor).retryPendingRefunds(now.minus(RefundRetryJob.GRACE));
    }
}
