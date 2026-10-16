package com.aryan.spring_security_demo.payment;

/**
 * Where a payment attempt stands, and the moves it can make.
 *
 * <pre>
 *   PROCESSING ──▶ SUCCEEDED
 *        │
 *        └──────▶ FAILED
 * </pre>
 *
 * <p>An attempt is {@code PROCESSING} from the moment it is recorded until the
 * provider's answer is: it is written before the provider is called, so an
 * attempt cut short in between is still on record. Its outcome never changes once
 * settled.
 */
public enum PaymentStatus {

    PROCESSING,
    SUCCEEDED,
    FAILED;

    /** Whether a payment in this state may move to {@code target}. */
    public boolean canTransitionTo(PaymentStatus target) {
        return this == PROCESSING && (target == SUCCEEDED || target == FAILED);
    }
}
