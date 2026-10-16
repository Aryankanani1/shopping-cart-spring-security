package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.common.ModuleConfiguration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Paying for orders: payment attempts, refunds, the payment provider and retries
 * (see {@code docs/payment-design.md}). {@link EnableResilientMethods} turns on
 * Spring's {@code @Retryable}, which {@link PaymentGatewayClient} uses;
 * {@link EnableAsync} turns on {@code @Async}, which runs
 * {@link RefundAfterCommitListener} on Spring Boot's task executor.
 * Scans only {@code com.aryan.spring_security_demo.payment}; see {@link ModuleConfiguration}.
 */
@ModuleConfiguration
@EnableResilientMethods
@EnableAsync
public class PaymentModule {
}
