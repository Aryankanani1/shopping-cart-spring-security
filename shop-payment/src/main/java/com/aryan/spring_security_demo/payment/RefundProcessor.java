package com.aryan.spring_security_demo.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Refunds a payment that is owed back: asks the provider, with no transaction
 * open, then records the refund. Used by {@link RefundAfterCommitListener} right
 * after a cancellation commits, and by {@link RefundRetryJob} for what is still
 * pending later. Safe to run twice for one payment: the provider key is the same
 * every time, so it refunds once, and recording a refund already recorded does
 * nothing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundProcessor {

    private final RefundRecorder recorder;
    private final PaymentGatewayClient gateway;

    /**
     * Send one refund to the provider, if it is still owed.
     *
     * @return false if the provider is still unavailable after the retries; the
     *         refund stays {@code PENDING} for the retry job
     */
    public boolean refund(Long refundId) {
        Optional<RefundRequest> owed = recorder.pendingRefund(refundId);
        if (owed.isEmpty()) {
            return true;  // refunded already, by the listener or the job
        }
        RefundAttempt attempt = gateway.refund(owed.get());
        if (attempt instanceof RefundAttempt.StillOwed stillOwed) {
            // The client's fallback: the refund stays PENDING for the retry job.
            log.warn("Refund {}: still owed ({}), the retry job will try again", refundId, stillOwed.reason());
            return false;
        }
        recorder.recordRefunded(refundId, ((RefundAttempt.Refunded) attempt).refund());
        return true;
    }

    /**
     * Retry every refund owed since before {@code requestedBefore}. Each one on its
     * own: one that fails is logged and left pending, and the others go on.
     *
     * @return how many are refunded now
     */
    public int retryPendingRefunds(Instant requestedBefore) {
        int refunded = 0;
        for (Long refundId : recorder.refundsPendingSince(requestedBefore)) {
            try {
                if (refund(refundId)) {
                    refunded++;
                }
            } catch (RuntimeException e) {
                log.warn("Refund {} failed, the retry job will try again", refundId, e);
            }
        }
        return refunded;
    }
}
