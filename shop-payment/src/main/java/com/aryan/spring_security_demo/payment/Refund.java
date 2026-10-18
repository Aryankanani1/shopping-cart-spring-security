package com.aryan.spring_security_demo.payment;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Money given back from a payment, in full or in part (V17;
 * docs/payment-design.md, section 11). {@code PENDING} from the moment it is owed,
 * in the transaction that owes it, so it is the outbox row the refund is sent and
 * retried from; {@code REFUNDED} once the provider has refunded it. Created only
 * through {@link Payment}, which checks there is that much left to refund.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "refund", uniqueConstraints = {
        @UniqueConstraint(name = "uk_refund_idempotency_key", columnNames = "idempotency_key"),
        @UniqueConstraint(name = "uk_refund_provider_reference", columnNames = "provider_reference"),
        @UniqueConstraint(name = "uk_refund_payment_request_key", columnNames = {"payment_id", "request_key"})
}, indexes = @Index(name = "idx_refund_status_requested", columnList = "status, requested_at"))
public class Refund {

    /** New refunds' keys with the provider; those V17 moved keep {@code refund_<payment id>}. */
    static final String KEY_PREFIX = "rfd_";

    /** How long {@code reason} may be (V17). */
    static final int MAX_REASON = 255;

    /** What the refund is for. */
    public enum Kind {
        /** An amount an admin chose, with a reason: a goodwill gesture, a damaged item kept. */
        AMOUNT,
        /** What was left when the order was cancelled. */
        CANCELLATION,
        /** Order items given back, priced at what the customer paid for them. */
        ITEMS
    }

    public enum Status { PENDING, REFUNDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    /** Shown to the customer. */
    @Column(length = MAX_REASON)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    /** The shop's key with the provider: every call to refund this one carries it. */
    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    /** The admin's {@code Idempotency-Key}; none for a cancellation's refund. */
    @Column(name = "request_key", length = 64)
    private String requestKey;

    /** The provider's id for the refund, once refunded. */
    @Column(name = "provider_reference", length = 64)
    private String providerReference;

    /** The admin's email at the time: a record, not a foreign key, so it outlives the account. */
    @Column(name = "requested_by")
    private String requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @BatchSize(size = 20)
    @OneToMany(mappedBy = "refund", cascade = CascadeType.ALL)
    private List<RefundItem> items = new ArrayList<>();

    /** A refund now owed from {@code payment}; {@link Payment} has checked the amount is left. */
    static Refund owed(Payment payment, Kind kind, BigDecimal amount, String reason, String requestKey,
                       String requestedBy, Instant now) {
        Refund refund = new Refund();
        refund.payment = payment;
        refund.kind = kind;
        refund.amount = amount;
        refund.currency = payment.getCurrency();
        refund.reason = reason == null || reason.length() <= MAX_REASON ? reason : reason.substring(0, MAX_REASON);
        refund.status = Status.PENDING;
        refund.idempotencyKey = KEY_PREFIX + UUID.randomUUID().toString().replace("-", "");
        refund.requestKey = requestKey;
        refund.requestedBy = requestedBy;
        refund.requestedAt = now;
        return refund;
    }

    /** An order item this {@code ITEMS} refund gives money back for. */
    void addItem(Long orderItemId, int quantity, BigDecimal itemAmount) {
        items.add(new RefundItem(this, orderItemId, quantity, itemAmount));
    }

    /** The provider refunded it: recorded through {@link Payment#refunded}, which adds it to the payment's total. */
    void refunded(String reference, Instant now) {
        this.status = Status.REFUNDED;
        this.providerReference = reference;
        this.refundedAt = now;
    }
}
