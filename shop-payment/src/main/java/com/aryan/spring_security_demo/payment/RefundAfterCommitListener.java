package com.aryan.spring_security_demo.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Starts the refunds a cancellation made owed, once its transaction has committed
 * (never if it rolls back, so no one is refunded for an order that wasn't
 * cancelled).
 *
 * <p>{@code @Async}, on a background thread: Spring runs an {@code AFTER_COMMIT}
 * listener before it releases the transaction's connection, so calling the
 * provider right there would hold the request's pooled connection for as long as
 * the provider takes. On another thread no connection is held, and the cancel
 * request doesn't wait for the provider.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefundAfterCommitListener {

    private final RefundProcessor refundProcessor;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRefundsRequested(RefundsRequestedEvent event) {
        for (Long paymentId : event.paymentIds()) {
            try {
                refundProcessor.refund(paymentId);
            } catch (RuntimeException e) {
                // The cancellation has committed, so there is nothing to roll back:
                // the payment stays REFUND_PENDING, and RefundRetryJob tries again.
                log.warn("Refund of payment {} failed, the retry job will try again", paymentId, e);
            }
        }
    }
}
