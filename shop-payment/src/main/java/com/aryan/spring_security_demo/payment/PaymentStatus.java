package com.aryan.spring_security_demo.payment;

/**
 * Where a payment attempt stands, and the moves it can make.
 *
 * <pre>
 *   PROCESSING ──▶ SUCCEEDED ──▶ REFUND_PENDING ──▶ REFUNDED
 *        │              │                              ▲
 *        └──▶ FAILED    └──────────────────────────────┘
 * </pre>
 *
 * <p>An attempt is {@code PROCESSING} from the moment it is recorded until the
 * provider's answer is: it is written before the provider is called, so an
 * attempt cut short in between is still on record. A charge that succeeded stays
 * {@code SUCCEEDED} while it is refunded in part ({@link Refund}s). It is
 * {@code REFUND_PENDING} once its order is cancelled, in the cancellation's
 * transaction, and {@code REFUNDED} once every cent is back, which partial refunds
 * can reach on their own.
 */
public enum PaymentStatus {

    PROCESSING,
    SUCCEEDED,
    FAILED,
    REFUND_PENDING,
    REFUNDED;

    /** Whether a payment in this state may move to {@code target}. */
    public boolean canTransitionTo(PaymentStatus target) {
        return switch (this) {
            case PROCESSING -> target == SUCCEEDED || target == FAILED;
            // REFUNDED directly once partial refunds add up to the amount paid.
            case SUCCEEDED -> target == REFUND_PENDING || target == REFUNDED;
            case REFUND_PENDING -> target == REFUNDED;
            case FAILED, REFUNDED -> false;
        };
    }
}
