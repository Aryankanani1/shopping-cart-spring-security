package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.common.ModuleConfiguration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * Paying for orders: payment attempts, the payment provider and retries (see
 * {@code docs/payment-design.md}). {@link EnableResilientMethods} turns on
 * Spring's {@code @Retryable}, which {@link PaymentGatewayClient} uses.
 * Scans only {@code com.aryan.spring_security_demo.payment}; see {@link ModuleConfiguration}.
 */
@ModuleConfiguration
@EnableResilientMethods
public class PaymentModule {
}
