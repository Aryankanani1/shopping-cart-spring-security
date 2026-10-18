package com.aryan.spring_security_demo.payment;

/**
 * The provider wasn't called: its circuit breaker is open, because its recent calls
 * kept failing (docs/payment-design.md, section 9). A kind of "unavailable", so every
 * caller already handles it safely; {@link PaymentGatewayClient} doesn't retry it,
 * since a retry would only be refused again.
 */
public class PaymentProviderCircuitOpenException extends PaymentProviderUnavailableException {

    public PaymentProviderCircuitOpenException(String message, Throwable cause) {
        super(message, cause);
    }
}
