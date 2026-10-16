package com.aryan.spring_security_demo.payment;

/**
 * Where a payment attempt stands, and the moves it can make.
 *
 * <pre>
 *   PROCESSING ──▶ SUCCEEDED ──▶ REFUND_PENDING ──▶ REFUNDED
 *        │
 *        └──────▶ FAILED
 * </pre>
 *
 * <p>An attempt is {@code PROCESSING} from the moment it is recorded until the
 * provider's answer is: it is written before the provider is called, so an
 * attempt cut short in between is still on record. A charge that succeeded moves
 * on only when its order is cancelled: {@code REFUND_PENDING} is written in the
 * cancellation's transaction, {@code REFUNDED} once the provider has refunded.
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
            case SUCCEEDED -> target == REFUND_PENDING;
            case REFUND_PENDING -> target == REFUNDED;
            case FAILED, REFUNDED -> false;
        };
    }
}
