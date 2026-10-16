package com.aryan.spring_security_demo.payment;

import java.math.BigDecimal;

/**
 * What the provider is asked to refund: the whole of the charge
 * {@code providerReference}. {@code idempotencyKey} ({@code refund_<payment id>})
 * is the same on every try, by the after-commit listener, its retries and the
 * retry job, so the provider refunds the charge once.
 */
public record RefundRequest(String idempotencyKey, String providerReference, BigDecimal amount, String currency) {
}
