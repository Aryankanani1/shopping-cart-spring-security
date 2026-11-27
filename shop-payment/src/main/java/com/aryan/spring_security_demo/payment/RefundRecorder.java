package com.aryan.spring_security_demo.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * The database steps of a refund ({@link Refund}) after it is owed: what to ask the
 * provider for, and recording its answer, each a short transaction of its own.
 * Owing one is written in the transaction that owes it, with the outbox message
 * that has it sent ({@link RefundOutboxHandler}): a cancellation's, by
 * {@link CheckoutSaga#orderCancelling}, or an admin's partial refund, by
 * {@link RefundService}.
 *
 * <p>Those later steps are {@code REQUIRES_NEW}, not the default {@code REQUIRED}.
 * On the background thread and in the retry job, where they run now, the two are
 * the same. But if they were ever called straight from an {@code AFTER_COMMIT}
 * listener, {@code REQUIRED} would join the transaction that had just committed,
 * which Spring keeps bound to the thread until it cleans up, and the write would
 * be silently lost.
 */
@Service
@RequiredArgsConstructor
public class RefundRecorder {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final CheckoutSaga checkoutSaga;
    private final Clock clock;

    /** What to ask the provider to refund, or empty once it is refunded. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<RefundRequest> pendingRefund(Long refundId) {
        return refundRepository.findById(refundId)
                .filter(refund -> refund.getStatus() == Refund.Status.PENDING)
                .map(refund -> new RefundRequest(refund.getIdempotencyKey(),
                        refund.getPayment().getProviderReference(), refund.getAmount(), refund.getCurrency()));
    }

    /**
     * Record the provider's refund, in a transaction of its own. Nothing to do if it
     * is already recorded: the outbox can deliver one refund's message more than
     * once, and a webhook can get here too.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRefunded(Long refundId, RefundResult result) {
        refunded(refundId, result);
    }

    /**
     * The same, inside the caller's transaction: a webhook records its event and
     * the refund together, so a repeat of the event changes nothing.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordRefundedInCurrentTransaction(Long refundId, RefundResult result) {
        refunded(refundId, result);
    }

    private void refunded(Long refundId, RefundResult result) {
        Long paymentId = refundRepository.findPaymentIdById(refundId)
                .orElseThrow(() -> new IllegalStateException("Refund " + refundId + " is missing"));
        // The payment is locked first, as everything that refunds it locks it, then the
        // refund. Locking reads return the rows as committed now, so recorders racing
        // for this refund take turns, and each sees the last's work.
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment " + paymentId + " is missing"));
        Refund refund = refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new IllegalStateException("Refund " + refundId + " is missing"));
        payment.refunded(refund, result.refundReference(), Instant.now(clock));
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            // Every cent is back: if the order was cancelled, its checkout is compensated.
            checkoutSaga.paymentRefunded(payment.getOrder().getId());
        }
    }
}
