package com.aryan.spring_security_demo.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Refunds a payment that is owed back: asks the provider, with no transaction
 * open, then records the refund. Run by {@link RefundOutboxHandler} for each
 * refund's outbox message: right after the transaction that owed it commits, and
 * again later while it is still owed. Safe to run twice for one refund: the
 * provider key is the same every time, so it refunds once, and recording a refund
 * already recorded does nothing.
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
     *         refund stays {@code PENDING}, and its outbox message is delivered again
     */
    public boolean refund(Long refundId) {
        Optional<RefundRequest> owed = recorder.pendingRefund(refundId);
        if (owed.isEmpty()) {
            return true;  // refunded already, by an earlier delivery or a webhook
        }
        RefundAttempt attempt = gateway.refund(owed.get());
        if (attempt instanceof RefundAttempt.StillOwed stillOwed) {
            // The client's fallback: the refund stays PENDING, and the outbox tries again.
            log.warn("Refund {}: still owed ({}), it will be tried again", refundId, stillOwed.reason());
            return false;
        }
        recorder.recordRefunded(refundId, ((RefundAttempt.Refunded) attempt).refund());
        return true;
    }
}
