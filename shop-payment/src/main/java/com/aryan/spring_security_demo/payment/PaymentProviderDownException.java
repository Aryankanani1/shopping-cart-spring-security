package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.common.exception.ServiceUnavailableException;

import java.time.Duration;

/**
 * A payment refused before it is even recorded, because the provider's circuit
 * breaker is open (docs/payment-design.md, section 9): a 503 with Retry-After, so an
 * outage at the provider doesn't use up the order's attempts.
 */
public class PaymentProviderDownException extends ServiceUnavailableException {

    public PaymentProviderDownException(Duration retryAfter) {
        super("Payments are paused: the payment provider keeps failing. Please try again in a moment.", retryAfter);
    }
}
