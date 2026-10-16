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
import jakarta.persistence.Index;
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
import java.util.Optional;

/**
 * One attempt to pay for an order (V11 and V12 migrations). Recorded as
 * {@link PaymentStatus#PROCESSING} before the provider is called, then settled
 * with the provider's answer, and refunded if its order is cancelled; rows are
 * never deleted, so every attempt stays on record. Its state changes only through
 * {@link #succeed}, {@link #fail}, {@link #requestRefund} and {@link #refunded},
 * which follow {@link PaymentStatus}. A refund never changes the charge's own
 * fields; it adds its own.
 *
 * <p>The database also allows at most one payment per order that hasn't failed
 * (a unique key on a generated column, see V11), whatever the code does.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "payment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_order_idempotency_key", columnNames = {"order_id", "idempotency_key"}),
        @UniqueConstraint(name = "uk_payment_provider_reference", columnNames = "provider_reference"),
        @UniqueConstraint(name = "uk_payment_refund_reference", columnNames = "refund_reference")
}, indexes = @Index(name = "idx_payment_status_refund_requested", columnList = "status, refund_requested_at"))
public class Payment {

    static final String CHARGE_KEY_PREFIX = "pay_";
    static final String REFUND_KEY_PREFIX = "refund_";

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

    /** The provider's id for the refund, once refunded. */
    @Column(name = "refund_reference", length = 64)
    private String refundReference;

    /** When the order was cancelled and the refund became owed. */
    @Column(name = "refund_requested_at")
    private Instant refundRequestedAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    /**
     * The idempotency key of every provider call for this attempt's charge: the
     * request's, its retries' and reconciliation's lookup. Ours, not the client's,
     * so two orders' attempts can never share one.
     */
    static String chargeKey(Long paymentId) {
        return CHARGE_KEY_PREFIX + paymentId;
    }

    /** The idempotency key of every provider call to refund this payment. */
    static String refundKey(Long paymentId) {
        return REFUND_KEY_PREFIX + paymentId;
    }

    /** The payment id in a key made by {@link #chargeKey} or {@link #refundKey}, if {@code key} is one. */
    static Optional<Long> idFromKey(String key, String prefix) {
        if (key == null || !key.startsWith(prefix)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(key.substring(prefix.length())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

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
        moveTo(PaymentStatus.SUCCEEDED);
        this.completedAt = now;
        this.providerReference = providerReference;
        this.cardBrand = cardBrand;
        this.cardLast4 = cardLast4;
    }

    /** The provider declined, or couldn't be reached; nothing was charged. */
    public void fail(String reason, Instant now) {
        moveTo(PaymentStatus.FAILED);
        this.completedAt = now;
        this.failureReason = reason;
    }

    /** The order was cancelled: the money is owed back, and the provider is yet to refund it. */
    public void requestRefund(Instant now) {
        moveTo(PaymentStatus.REFUND_PENDING);
        this.refundRequestedAt = now;
    }

    /** The provider gave the money back. */
    public void refunded(String refundReference, Instant now) {
        moveTo(PaymentStatus.REFUNDED);
        this.refundReference = refundReference;
        this.refundedAt = now;
    }

    private void moveTo(PaymentStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Payment " + id + " is " + status + " and can't become " + target);
        }
        this.status = target;
    }
}
