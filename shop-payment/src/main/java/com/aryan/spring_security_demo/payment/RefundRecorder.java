package com.aryan.spring_security_demo.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The database steps of a refund ({@link Refund}). Owing one is written in the
 * transaction that owes it (a cancellation's, or an admin's partial refund in
 * {@link RefundService}); the steps after the provider call are short transactions
 * of their own.
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
    private final Clock clock;

    /**
     * Owe back what is left of the payments of orders being cancelled. Joins the
     * cancellation's transaction, so the refund is owed if and only if the
     * cancellation commits: the refund row is the outbox it is retried from. The
     * payments are locked first, as a partial refund locks them, so the two can't
     * both count the same money.
     *
     * @return the refunds now owed
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> requestRefunds(Collection<Long> orderIds) {
        Instant now = Instant.now(clock);
        List<Refund> owed = new ArrayList<>();
        for (Payment payment : paymentRepository.findByOrdersAndStatusForUpdate(orderIds, PaymentStatus.SUCCEEDED)) {
            payment.refundTheRest(now).ifPresent(owed::add);
        }
        // Saved now, not at the flush, so that their ids are known.
        return refundRepository.saveAll(owed).stream().map(Refund::getId).toList();
    }

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
     * is already recorded: the after-commit listener, the retry job and a webhook
     * can all get here for one refund.
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
    }

    /** The refunds owed since before {@code requestedBefore} and still owed, oldest first. */
    @Transactional(readOnly = true)
    public List<Long> refundsPendingSince(Instant requestedBefore) {
        return refundRepository.findIdsByStatusRequestedBefore(Refund.Status.PENDING, requestedBefore);
    }
}
