package com.aryan.spring_security_demo.payment;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentReconciliationJobTest {

    // An attempt younger than the grace period may still be settled by its own request.
    @Test
    void run_reconcilesThePaymentsStartedBeforeTheGracePeriod() {
        PaymentReconciler reconciler = mock(PaymentReconciler.class);
        Instant now = Instant.parse("2026-10-16T09:00:00Z");

        new PaymentReconciliationJob(reconciler, Clock.fixed(now, ZoneOffset.UTC)).run();

        verify(reconciler).reconcileStuckPayments(now.minus(PaymentReconciliationJob.GRACE));
    }
}
