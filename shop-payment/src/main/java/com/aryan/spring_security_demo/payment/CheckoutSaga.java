package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.common.outbox.Outbox;
import com.aryan.spring_security_demo.order.OrderServiceInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The checkout saga's orchestrator (docs/payment-design.md, section 13). A checkout
 * is three steps, one of them at the payment provider, so no single transaction can
 * cover it; each step that can be undone has a compensation instead:
 *
 * <pre>
 *   step                                       compensation
 *   1. book the order: stock reserved          cancel the order, restock     (OrderService)
 *   2. charge the payment at the provider      refund it                     (outbox: payment.refund)
 *   3. confirm the order: PAID                 none: the pivot; from here the checkout only goes forward
 * </pre>
 *
 * <p>Each step's outcome is reported here, in the transaction that records it, and
 * this decides what follows: the saga completes, the customer may try paying again,
 * or the steps done so far are compensated. A charge the provider makes after the
 * shop recorded the attempt as failed is step 2 completing out of turn, and is
 * compensated too ({@link #chargedAfterFailing}). Compensations owed after a commit are
 * written to the outbox in the same transaction, so none is lost. The saga's state
 * is one {@link CheckoutSagaInstance} per order, changed only here, and only under
 * its row lock, which is the last lock each of these transactions takes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutSaga {

    private final CheckoutSagaInstanceRepository sagas;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final OrderServiceInterface orderService;
    private final Outbox outbox;
    private final Clock clock;

    /** Step 1 is done: the order is booked and its stock reserved. In the checkout's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void orderBooked(Long orderId) {
        sagas.save(CheckoutSagaInstance.started(orderId, Instant.now(clock)));
    }

    /**
     * Steps 2 and 3 are done: the provider charged, and the order is paid. In the
     * transaction that records the charge, which locked the payment and the order.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void paymentSucceeded(Long orderId) {
        Instant now = Instant.now(clock);
        lock(List.of(orderId)).forEach(saga -> saga.completed(now));
    }

    /**
     * Step 2 failed: nothing was charged, and the customer may try again. After the
     * last attempt allowed, step 1 is compensated: the saga is compensating, and the
     * order's cancellation is owed through the outbox, in this same transaction. It
     * runs once this commits, in a transaction of its own ({@link #compensate}), so
     * recording the failure (a request, a webhook or reconciliation) can't fail with it.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void paymentFailed(Long orderId) {
        if (paymentRepository.countByOrderAndStatus(orderId, PaymentStatus.FAILED) < PaymentRecorder.MAX_FAILED_ATTEMPTS) {
            return;
        }
        Instant now = Instant.now(clock);
        for (CheckoutSagaInstance saga : lock(List.of(orderId))) {
            if (saga.getStatus() == CheckoutSagaStatus.AWAITING_PAYMENT) {
                saga.compensating(CompensationReason.PAYMENT_ATTEMPTS_USED_UP, now);
                outbox.append(CheckoutCompensationHandler.TYPE, orderId);
            }
        }
    }

    /**
     * Compensate step 1 of a checkout whose payments all failed: cancel the order and
     * restock it. A transaction of its own, run from the outbox. Nothing to do if the
     * order was cancelled meanwhile (by its owner, say): a repeated delivery finds it so.
     */
    @Transactional
    public void compensate(Long orderId) {
        if (!orderService.cancelUnpaid(orderId)) {
            log.info("Checkout of order {}: the order is no longer pending, nothing to cancel", orderId);
        }
        // Cancelling it called orderCancelling below, which marked the saga.
    }

    /**
     * The order was left unpaid past its deadline: compensate step 1, cancelling it
     * and restocking. A transaction of its own, for {@link CheckoutDeadlineJob}.
     *
     * @return false if it was paid or cancelled in the meantime
     */
    @Transactional
    public boolean expire(Long orderId, Instant placedBefore) {
        if (!orderService.expireIfUnpaid(orderId, placedBefore)) {
            return false;
        }
        // Cancelling it called orderCancelling, which marked the saga; this says why.
        lock(List.of(orderId)).forEach(saga -> saga.compensatedBecause(CompensationReason.PAYMENT_DEADLINE_PASSED));
        return true;
    }

    /**
     * Orders are being cancelled, by anyone and for any reason: step 1 is being
     * undone by the order module, and this compensates step 2 for the paid ones,
     * owing back what is left of each payment. In the cancellation's transaction
     * ({@code MANDATORY}), so the refunds are owed if and only if it commits; and
     * refusing rolls it back.
     *
     * <p>An order being paid for can't be cancelled: the outcome of step 2 isn't
     * known yet, and the provider may still take the money, which would then be kept
     * for a cancelled order. Starting a payment raises the order's version, so a
     * cancellation that read the order just before can't slip through either.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void orderCancelling(List<Long> orderIds) {
        if (paymentRepository.countByOrdersAndStatus(orderIds, PaymentStatus.PROCESSING) > 0) {
            throw new PaymentInProgressException("The order can't be cancelled while its payment is being processed");
        }
        refundPayments(orderIds);
        Set<Long> refundOwed = new HashSet<>(paymentRepository.findOrderIdsByStatus(orderIds, PaymentStatus.REFUND_PENDING));
        Instant now = Instant.now(clock);
        lock(orderIds).forEach(saga -> saga.orderCancelled(refundOwed.contains(saga.getOrderId()), now));
    }

    /**
     * Step 2 completed out of turn: the provider reports charging an attempt the shop
     * recorded as failed, and told the customer so. By now they may have paid with
     * another attempt, or the order may be cancelled; so the charge doesn't pay for the
     * order, and is compensated instead: all of it is owed back, with the outbox
     * message that has it refunded, in the transaction that records the late charge.
     * The order and its saga stay as they are. Nothing more is owed if the late charge
     * is recorded already (the provider repeating its webhook).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void chargedAfterFailing(Payment payment, ChargeResult.Approved approved) {
        payment.chargedLate(approved.providerReference(), approved.cardBrand(), approved.cardLast4(),
                Instant.now(clock)).ifPresent(refund -> {
            refundRepository.save(refund);
            outbox.append(RefundOutboxHandler.TYPE, refund.getId());
            log.warn("Payment {} was charged after it had failed; refund {} of {} {} is owed", payment.getId(),
                    refund.getId(), refund.getAmount(), refund.getCurrency());
        });
    }

    /**
     * A payment is refunded in full. If its order's checkout was being compensated,
     * that was the last compensation owed. A payment refunded by partial refunds
     * alone, its order not cancelled, leaves the saga completed. In the transaction
     * that records the refund.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void paymentRefunded(Long orderId) {
        Instant now = Instant.now(clock);
        for (CheckoutSagaInstance saga : lock(List.of(orderId))) {
            if (saga.getStatus() == CheckoutSagaStatus.COMPENSATING) {
                saga.refunded(now);
            }
        }
    }

    /**
     * Compensate step 2: owe back what is left of each paid order's payment, with the
     * outbox message that has it sent to the provider. The payments are locked first,
     * as a partial refund locks them, so the two can't both count the same money.
     */
    private void refundPayments(Collection<Long> orderIds) {
        Instant now = Instant.now(clock);
        List<Refund> owed = new ArrayList<>();
        for (Payment payment : paymentRepository.findByOrdersAndStatusForUpdate(orderIds, PaymentStatus.SUCCEEDED)) {
            payment.refundTheRest(now).ifPresent(owed::add);
        }
        // Saved now, not at the flush, so that their ids are known.
        refundRepository.saveAll(owed).forEach(refund -> outbox.append(RefundOutboxHandler.TYPE, refund.getId()));
    }

    /**
     * The orders' sagas, locked. Every order placed at checkout has one, and V19 gave
     * one to each order placed before; an order made any other way has none, and
     * nothing to record.
     */
    private List<CheckoutSagaInstance> lock(Collection<Long> orderIds) {
        return sagas.findByOrderIdsForUpdate(orderIds);
    }
}
