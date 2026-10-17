package com.aryan.spring_security_demo.payment;

import java.math.BigDecimal;

/**
 * What the provider is asked to charge. {@code idempotencyKey} is ours, not the
 * client's ({@code pay_<payment id>}): it is the same on every retry of one
 * attempt, so the provider charges that attempt at most once.
 */
public record ChargeRequest(String idempotencyKey, BigDecimal amount, String currency, String paymentMethod) {

    /** The request for one payment attempt; the same every time it is built for that attempt. */
    static ChargeRequest forPayment(Long paymentId, BigDecimal amount, String currency, String paymentMethod) {
        return new ChargeRequest(Payment.chargeKey(paymentId), amount, currency, paymentMethod);
    }
}
