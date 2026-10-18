package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.order.Order;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One attempt to pay for an order (V11, V12 and V17 migrations). Recorded as
 * {@link PaymentStatus#PROCESSING} before the provider is called, then settled
 * with the provider's answer; rows are never deleted, so every attempt stays on
 * record. Its state changes only through {@link #succeed}, {@link #fail},
 * {@link #refund}, {@link #refundTheRest} and {@link #refunded}, which follow
 * {@link PaymentStatus}.
 *
 * <p>It is refunded through {@link Refund}s, in full or in part (section 11), and
 * never by more than was paid: each is added here, by a caller holding the
 * payment's row lock, after checking what is left against the totals on this row.
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

    /** How long {@code failure_reason} may be (V11). */
    static final int MAX_FAILURE_REASON = 255;

    static final String CHARGE_KEY_PREFIX = "pay_";

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
    @Column(name = "failure_reason", length = MAX_FAILURE_REASON)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Every refund owed or made so far, added up, and the part of that the provider
     * has refunded (V17, which checks they never exceed the amount paid). Kept on
     * this row rather than summed from {@link #refunds}, because what refunds it
     * locks the row first: a locking read returns the row as committed now, whereas
     * refunds loaded after it may be older. MySQL's {@code REPEATABLE READ} shows a
     * transaction's plain reads the database as it was at the first of them, and a
     * cancellation has read its order by the time it locks the payment.
     */
    @Column(name = "refund_requested_amount", nullable = false, precision = 38, scale = 2)
    private BigDecimal refundRequestedAmount = BigDecimal.ZERO;

    @Column(name = "refunded_amount", nullable = false, precision = 38, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    /** Every refund owed or made from this payment, oldest first. */
    @BatchSize(size = 20)
    @OneToMany(mappedBy = "payment", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<Refund> refunds = new ArrayList<>();

    /**
     * The idempotency key of every provider call for this attempt's charge: the
     * request's, its retries' and reconciliation's lookup. Ours, not the client's,
     * so two orders' attempts can never share one.
     */
    static String chargeKey(Long paymentId) {
        return CHARGE_KEY_PREFIX + paymentId;
    }

    /** The payment id in a key made by {@link #chargeKey}, if {@code key} is one. */
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
        this.failureReason = fitted(reason);
    }

    /** A reason cut to fit its column: a provider's explanation can be longer. */
    private static String fitted(String reason) {
        if (reason == null || reason.length() <= MAX_FAILURE_REASON) {
            return reason;
        }
        return reason.substring(0, MAX_FAILURE_REASON - 1) + "…";
    }

    /** What is left to refund: the amount paid, less every refund owed or made. */
    public BigDecimal refundableAmount() {
        return amount.subtract(refundRequestedAmount);
    }

    /** The refund requested under the admin's {@code requestKey}, if there is one. */
    public Optional<Refund> refundRequestedAs(String requestKey) {
        return refunds.stream().filter(refund -> requestKey.equals(refund.getRequestKey())).findFirst();
    }

    /**
     * Refuse a refund unless the payment succeeded and its order isn't cancelled.
     *
     * @throws RefundNotPossibleException if it can't be refunded now, saying why
     */
    public void requireRefundable() {
        if (status != PaymentStatus.SUCCEEDED) {
            throw new RefundNotPossibleException(switch (status) {
                case REFUND_PENDING -> "The order was cancelled: its payment is being refunded in full";
                case REFUNDED -> "The payment has been refunded in full";
                default -> "The order isn't paid";
            });
        }
    }

    /**
     * Owe back part of a payment that succeeded (an admin's refund of items or an
     * amount). The caller holds the payment's row lock, so what is left can't
     * change under it.
     *
     * @throws RefundNotPossibleException if the payment isn't one that succeeded
     *         (its order is unpaid, or cancelled), or less than {@code refundAmount}
     *         is left
     */
    public Refund refund(Refund.Kind kind, BigDecimal refundAmount, String reason, String requestKey,
                         String requestedBy, Instant now) {
        requireRefundable();
        if (refundAmount.signum() <= 0) {
            throw new RefundNotPossibleException("There is nothing to refund");
        }
        BigDecimal left = refundableAmount();
        if (refundAmount.compareTo(left) > 0) {
            throw new RefundNotPossibleException("Only " + left.toPlainString() + " " + currency
                    + " is left to refund, not " + refundAmount.toPlainString());
        }
        Refund refund = Refund.owed(this, kind, refundAmount, reason, requestKey, requestedBy, now);
        refunds.add(refund);
        refundRequestedAmount = refundRequestedAmount.add(refundAmount);
        return refund;
    }

    /**
     * The order was cancelled: whatever is left is owed back, and the payment is
     * {@code REFUND_PENDING} until every cent is.
     *
     * @return the refund of what was left, or empty if nothing was
     */
    public Optional<Refund> refundTheRest(Instant now) {
        BigDecimal left = refundableAmount();
        moveTo(PaymentStatus.REFUND_PENDING);
        if (left.signum() == 0) {
            return Optional.empty();  // the refunds already owed cover it all
        }
        Refund refund = Refund.owed(this, Refund.Kind.CANCELLATION, left, "Order cancelled", null, null, now);
        refunds.add(refund);
        refundRequestedAmount = amount;
        return Optional.of(refund);
    }

    /**
     * The provider refunded {@code refund}, one of this payment's. Once every cent
     * is back, the payment is {@code REFUNDED}. Nothing changes if that is recorded
     * already.
     */
    public void refunded(Refund refund, String reference, Instant now) {
        if (refund.getStatus() == Refund.Status.REFUNDED) {
            return;
        }
        refund.refunded(reference, now);
        refundedAmount = refundedAmount.add(refund.getAmount());
        if (refundedAmount.compareTo(amount) == 0) {
            moveTo(PaymentStatus.REFUNDED);
        }
    }

    private void moveTo(PaymentStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Payment " + id + " is " + status + " and can't become " + target);
        }
        this.status = target;
    }
}
