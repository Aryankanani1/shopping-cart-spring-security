package com.aryan.spring_security_demo.payment;

/**
 * The provider couldn't be reached, so it didn't take the charge. Unlike a
 * timeout after the request was sent, this is safe to retry, and with the same
 * idempotency key {@link PaymentGatewayClient} does. It never reaches the client:
 * once the retries run out, the attempt is recorded as failed.
 */
public class PaymentProviderUnavailableException extends RuntimeException {

    public PaymentProviderUnavailableException(String message) {
        super(message);
    }
}
