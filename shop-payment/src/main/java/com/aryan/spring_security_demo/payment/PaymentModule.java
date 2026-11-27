package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.common.ModuleConfiguration;

/**
 * Paying for orders: payment attempts, refunds, the payment provider and retries
 * (see {@code docs/payment-design.md}). The retries and the circuit breaker are
 * Resilience4j's, configured in {@code application.yml}; refunds are sent through
 * the outbox ({@link RefundOutboxHandler}).
 * Scans only {@code com.aryan.spring_security_demo.payment}; see {@link ModuleConfiguration}.
 */
@ModuleConfiguration
public class PaymentModule {
}
