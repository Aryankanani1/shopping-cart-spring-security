package com.aryan.spring_security_demo.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One order's checkout saga: how far it got, and why it was undone if it was
 * (V19 migration; docs/payment-design.md, section 13). Changed only by
 * {@link CheckoutSaga}, under the row's lock, through the moves
 * {@link CheckoutSagaStatus} allows.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "checkout_saga",
        uniqueConstraints = @UniqueConstraint(name = "uk_checkout_saga_order", columnNames = "order_id"))
public class CheckoutSagaInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The order whose checkout this is; one saga per order. */
    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckoutSagaStatus status;

    /** Why it was compensated; null while it hasn't been. */
    @Enumerated(EnumType.STRING)
    @Column(name = "compensation_reason")
    private CompensationReason compensationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Step 1 is done: the order is booked, and waits for its payment. */
    static CheckoutSagaInstance started(Long orderId, Instant now) {
        CheckoutSagaInstance saga = new CheckoutSagaInstance();
        saga.orderId = orderId;
        saga.status = CheckoutSagaStatus.AWAITING_PAYMENT;
        saga.createdAt = now;
        saga.updatedAt = now;
        return saga;
    }

    /** Steps 2 and 3 are done: the payment succeeded and the order is paid. */
    void completed(Instant now) {
        moveTo(CheckoutSagaStatus.COMPLETED, now);
    }

    /** A compensation is owed, for {@code reason}, before anything is undone. */
    void compensating(CompensationReason reason, Instant now) {
        moveTo(CheckoutSagaStatus.COMPENSATING, now);
        compensationReason = reason;
    }

    /**
     * The order is cancelled: step 1 is undone. Still compensating while a refund
     * of step 2 is owed; compensated if nothing is. Keeps the reason it was given
     * before, if any.
     */
    void orderCancelled(boolean refundOwed, Instant now) {
        CheckoutSagaStatus target = refundOwed ? CheckoutSagaStatus.COMPENSATING : CheckoutSagaStatus.COMPENSATED;
        if (status != target) {
            moveTo(target, now);
        }
        if (compensationReason == null) {
            compensationReason = CompensationReason.ORDER_CANCELLED;
        }
    }

    /** The order's payment is refunded in full: the last compensation is done. */
    void refunded(Instant now) {
        moveTo(CheckoutSagaStatus.COMPENSATED, now);
    }

    /** Record why it was compensated, when the cause is known only after the order is cancelled. */
    void compensatedBecause(CompensationReason reason) {
        compensationReason = reason;
    }

    private void moveTo(CheckoutSagaStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("The checkout saga of order " + orderId + " is " + status
                    + " and can't become " + target);
        }
        status = target;
        updatedAt = now;
    }
}
