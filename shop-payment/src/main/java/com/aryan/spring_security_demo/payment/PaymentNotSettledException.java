package com.aryan.spring_security_demo.payment;

/**
 * The provider answered a lookup, but its answer doesn't settle the payment yet: it
 * is still processing it, or it won't replay a request that has changed since (which
 * a person has to settle). The payment stays {@code PROCESSING} for a later run, as
 * for "unavailable". But the provider is working, so its circuit breaker ignores this
 * (application.yml): otherwise one stuck payment, looked up every minute, could open
 * the breaker on a quiet shop and stop everyone's payments.
 */
public class PaymentNotSettledException extends PaymentProviderUnavailableException {

    public PaymentNotSettledException(String message) {
        super(message);
    }
}
