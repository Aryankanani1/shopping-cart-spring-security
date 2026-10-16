package com.aryan.spring_security_demo.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Retries refunds still owed: ones the after-commit attempt couldn't finish (the
 * provider was down) or never started (the app stopped right after the
 * cancellation committed). Runs on {@code app.payment.refund-retry-cron}, on one
 * instance at a time (ShedLock).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefundRetryJob {

    /** A refund owed for less than this is left to the after-commit attempt, which is likely still running. */
    static final Duration GRACE = Duration.ofMinutes(1);

    private final RefundProcessor refundProcessor;
    private final Clock clock;

    @Scheduled(cron = "${app.payment.refund-retry-cron}")
    @SchedulerLock(name = "paymentRefundRetry", lockAtMostFor = "PT5M", lockAtLeastFor = "PT20S")
    public void run() {
        int refunded = refundProcessor.retryPendingRefunds(clock.instant().minus(GRACE));
        if (refunded > 0) {
            log.info("Refund retry: refunded {} payment(s)", refunded);
        }
    }
}
