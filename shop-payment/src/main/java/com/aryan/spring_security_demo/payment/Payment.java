package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.order.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One attempt to pay for an order (V11 migration). Recorded as
 * {@link PaymentStatus#PROCESSING} before the provider is called, then settled
 * with the provider's answer; rows are never deleted, so every attempt stays on
 * record. Its state changes only through {@link #succeed} and {@link #fail},
 * which follow {@link PaymentStatus}.
 *
 * <p>The database also allows at most one payment per order that hasn't failed
 * (a unique key on a generated column, see V11), whatever the code does.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "payment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_order_idempotency_key", columnNames = {"order_id", "idempotency_key"}),
        @UniqueConstraint(name = "uk_payment_provider_reference", columnNames = "provider_reference")
})
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    /** Chosen by the client; the same key for the same order means the same attempt. */
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 64)
    private String idempotencyKey;

    /** The payment method the provider was asked to charge (a test method here, a provider token in real life). */
    @Column(name = "payment_method", nullable = false, updatable = false, length = 64)
    private String paymentMethod;

    /** The order's total when the attempt started, never a client-sent amount. */
    @Column(nullable = false, updatable = false, precision = 38, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    /** The provider's id for the charge, once approved. */
    @Column(name = "provider_reference", length = 64)
    private String providerReference;

    @Column(name = "card_brand", length = 20)
    private String cardBrand;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    /** Why the attempt failed, in words the customer can be shown. */
    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** A new attempt to pay {@code order}'s total, not yet sent to the provider. */
    public static Payment start(Order order, String idempotencyKey, String paymentMethod, String currency, Instant now) {
        Payment payment = new Payment();
        payment.order = order;
        payment.idempotencyKey = idempotencyKey;
        payment.paymentMethod = paymentMethod;
        payment.amount = order.getTotalAmount();
        payment.currency = currency;
        payment.status = PaymentStatus.PROCESSING;
        payment.createdAt = now;
        return payment;
    }

    /** The provider took the money. */
    public void succeed(String providerReference, String cardBrand, String cardLast4, Instant now) {
        moveTo(PaymentStatus.SUCCEEDED, now);
        this.providerReference = providerReference;
        this.cardBrand = cardBrand;
        this.cardLast4 = cardLast4;
    }

    /** The provider declined, or couldn't be reached; nothing was charged. */
    public void fail(String reason, Instant now) {
        moveTo(PaymentStatus.FAILED, now);
        this.failureReason = reason;
    }

    private void moveTo(PaymentStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Payment " + id + " is " + status + " and can't become " + target);
        }
        this.status = target;
        this.completedAt = now;
    }
}
