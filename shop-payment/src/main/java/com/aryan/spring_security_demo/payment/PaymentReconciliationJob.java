package com.aryan.spring_security_demo.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

/**
 * Settles payments that have been {@code PROCESSING} for too long (see
 * {@link PaymentReconciler}). Runs on {@code app.payment.reconcile-cron}, on one
 * instance at a time (ShedLock).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationJob {

    /**
     * Well past any request's provider timeout: an attempt younger than this may
     * still be settled by its own request, and a charge not received by then never
     * will be.
     */
    static final Duration GRACE = Duration.ofMinutes(2);

    private final PaymentReconciler reconciler;
    private final Clock clock;

    @Scheduled(cron = "${app.payment.reconcile-cron}")
    @SchedulerLock(name = "paymentReconciliation", lockAtMostFor = "PT5M", lockAtLeastFor = "PT20S")
    public void run() {
        int settled = reconciler.reconcileStuckPayments(clock.instant().minus(GRACE));
        if (settled > 0) {
            log.info("Payment reconciliation: settled {} payment(s)", settled);
        }
    }
}
