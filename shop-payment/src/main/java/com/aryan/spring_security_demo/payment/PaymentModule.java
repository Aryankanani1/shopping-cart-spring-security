package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.common.ModuleConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Paying for orders: payment attempts, refunds, the payment provider and retries
 * (see {@code docs/payment-design.md}). The retries and the circuit breaker are
 * Resilience4j's, configured in {@code application.yml};
 * {@link EnableAsync} turns on {@code @Async}, which runs
 * {@link RefundAfterCommitListener} on Spring Boot's task executor.
 * Scans only {@code com.aryan.spring_security_demo.payment}; see {@link ModuleConfiguration}.
 */
@ModuleConfiguration
@EnableAsync
public class PaymentModule {
}
