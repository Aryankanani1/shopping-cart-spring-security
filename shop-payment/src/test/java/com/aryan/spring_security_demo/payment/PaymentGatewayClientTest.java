package com.aryan.spring_security_demo.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.resilience.annotation.EnableResilientMethods;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Which provider failures are retried, through Spring's {@code @Retryable} proxy: an
 * unavailable provider is, but not a call its open circuit breaker refused, nor a
 * lookup the provider answered without settling (docs/payment-design.md, section 9).
 */
class PaymentGatewayClientTest {

    private static final ChargeRequest CHARGE =
            new ChargeRequest("pay_7", new BigDecimal("25.00"), "USD", FakePaymentGateway.VISA);

    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(Retries.class)
            .withBean(PaymentGateway.class, () -> gateway);

    @Test
    void anUnavailableProvider_isTriedThreeTimes() {
        when(gateway.charge(CHARGE)).thenThrow(new PaymentProviderUnavailableException("down"));

        context.run(started -> {
            PaymentGatewayClient client = started.getBean(PaymentGatewayClient.class);
            assertThatThrownBy(() -> client.charge(CHARGE)).isInstanceOf(PaymentProviderUnavailableException.class);
            verify(gateway, times(3)).charge(CHARGE);
        });
    }

    @Test
    void aCallTheOpenBreakerRefused_isNotRetried() {
        when(gateway.charge(CHARGE)).thenThrow(new PaymentProviderCircuitOpenException("open", null));

        context.run(started -> {
            PaymentGatewayClient client = started.getBean(PaymentGatewayClient.class);
            assertThatThrownBy(() -> client.charge(CHARGE)).isInstanceOf(PaymentProviderCircuitOpenException.class);
            verify(gateway, times(1)).charge(CHARGE);
        });
    }

    @Test
    void aLookupTheProviderAnsweredWithoutSettling_isNotRetried() {
        when(gateway.findCharge(CHARGE)).thenThrow(new PaymentNotSettledException("still processing"));

        context.run(started -> {
            PaymentGatewayClient client = started.getBean(PaymentGatewayClient.class);
            assertThatThrownBy(() -> client.findCharge(CHARGE)).isInstanceOf(PaymentNotSettledException.class);
            verify(gateway, times(1)).findCharge(CHARGE);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableResilientMethods
    @Import(PaymentGatewayClient.class)
    static class Retries {
    }
}
