package com.aryan.spring_security_demo.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The database steps of a refund. Owing it is written in the cancellation's
 * transaction; the steps after the provider call are short transactions of their
 * own.
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
    private final Clock clock;

    /**
     * Mark the succeeded payments of orders being cancelled as owed back. Joins the
     * cancellation's transaction, so the refund is owed if and only if the
     * cancellation commits: this is the outbox row the refund is retried from.
     *
     * @return the payments now waiting to be refunded
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> requestRefunds(Collection<Long> orderIds) {
        Instant now = Instant.now(clock);
        List<Payment> paid = paymentRepository.findByOrdersAndStatus(orderIds, PaymentStatus.SUCCEEDED);
        paid.forEach(payment -> payment.requestRefund(now));
        return paid.stream().map(Payment::getId).toList();
    }

    /** What to ask the provider to refund for a payment, or empty once it is refunded. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<RefundRequest> pendingRefund(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .filter(payment -> payment.getStatus() == PaymentStatus.REFUND_PENDING)
                .map(payment -> new RefundRequest("refund_" + payment.getId(), payment.getProviderReference(),
                        payment.getAmount(), payment.getCurrency()));
    }

    /**
     * Record the provider's refund. Nothing to do if it is already recorded: the
     * after-commit listener and the retry job can both get here for one refund.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRefunded(Long paymentId, RefundResult result) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Payment " + paymentId + " is missing"));
        if (payment.getStatus() == PaymentStatus.REFUND_PENDING) {
            payment.refunded(result.refundReference(), Instant.now(clock));
        }
    }

    /** The payments whose refund was owed before {@code requestedBefore} and still is, oldest first. */
    @Transactional(readOnly = true)
    public List<Long> refundsPendingSince(Instant requestedBefore) {
        return paymentRepository.findIdsByStatusRequestedBefore(PaymentStatus.REFUND_PENDING, requestedBefore);
    }
}
